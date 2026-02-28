package banking_app.controller;

import banking_app.entity.User;
import banking_app.entity.Account;
import banking_app.entity.Transaction;
import banking_app.repository.UserRepository;
import banking_app.repository.AccountRepository;
import banking_app.repository.TransactionRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;  // Make sure this is present

    @Autowired
    private TransactionRepository transactionRepository;

    // 🔐 Check admin session
    private boolean isAdmin(HttpSession session) {
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        return loggedInUser != null && "ADMIN".equals(loggedInUser.getRole());
    }

    // ===============================
    // 📊 Admin Dashboard
    // ===============================
    @GetMapping("/dashboard")
    public String adminDashboard(Model model, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        long totalUsers = userRepository.count();
        long totalAccounts = accountRepository.count();
        long totalTransactions = transactionRepository.count();
        
        // Calculate total balance across all accounts
        double totalBalance = 0.0;
        try {
            List<Account> allAccounts = accountRepository.findAll();
            for (Account acc : allAccounts) {
                if (acc.getBalance() != null) {
                    totalBalance += acc.getBalance();
                }
            }
        } catch (Exception e) {
            System.out.println("Error calculating total balance: " + e.getMessage());
        }

        // Get recent transactions (last 10)
        List<Transaction> recentTransactions = new ArrayList<>();
        try {
            recentTransactions = transactionRepository.findTop10ByOrderByDateTimeDesc();
        } catch (Exception e) {
            System.out.println("Error fetching recent transactions: " + e.getMessage());
        }
        
        // Format dates for display
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        if (recentTransactions != null) {
            for (Transaction tx : recentTransactions) {
                if (tx.getDateTime() != null) {
                    tx.setFormattedDateTime(tx.getDateTime().format(formatter));
                }
            }
        }

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalAccounts", totalAccounts);
        model.addAttribute("totalBalance", totalBalance);
        model.addAttribute("totalTransactions", totalTransactions);
        model.addAttribute("recentTransactions", recentTransactions != null ? recentTransactions : new ArrayList<>());

        return "admin/dashboard";
    }

    // ===============================
    // 👥 View Users
    // ===============================
    @GetMapping("/users")
    public String viewUsers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page,
            Model model,
            HttpSession session) {

        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        Pageable pageable = PageRequest.of(page, 10);
        Page<User> userPage;

        try {
            if (keyword != null && !keyword.isEmpty()) {
                userPage = userRepository
                        .findByUsernameContainingOrEmailContainingOrNameContaining(
                                keyword, keyword, keyword, pageable);
            } else {
                userPage = userRepository.findAll(pageable);
            }
        } catch (Exception e) {
            System.out.println("Error fetching users: " + e.getMessage());
            userPage = Page.empty();
        }

        // Get accounts for each user
        Map<Long, Account> userAccounts = new HashMap<>();
        if (userPage.hasContent()) {
            for (User user : userPage.getContent()) {
                try {
                    Account account = accountRepository.findByUser(user);
                    if (account != null) {
                        userAccounts.put(user.getId(), account);
                    }
                } catch (Exception e) {
                    System.out.println("Error fetching account for user " + user.getId() + ": " + e.getMessage());
                }
            }
        }

        model.addAttribute("users", userPage.getContent());
        model.addAttribute("userAccounts", userAccounts);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", userPage.getTotalPages());
        model.addAttribute("keyword", keyword);

        return "admin/users";
    }

    // ===============================
    // 👤 View Single User
    // ===============================
    @GetMapping("/user/{id}")
    public String viewUser(@PathVariable Long id, Model model, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return "redirect:/admin/users";
        }

        Account account = null;
        List<Transaction> transactions = new ArrayList<>();
        
        try {
            account = accountRepository.findByUser(user);
        } catch (Exception e) {
            System.out.println("Error fetching account: " + e.getMessage());
        }
        
        try {
            transactions = transactionRepository.findByUser(user);
        } catch (Exception e) {
            System.out.println("Error fetching transactions: " + e.getMessage());
        }
        
        // Format dates
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        if (transactions != null) {
            for (Transaction tx : transactions) {
                if (tx.getDateTime() != null) {
                    tx.setFormattedDateTime(tx.getDateTime().format(formatter));
                }
            }
        }

        model.addAttribute("user", user);
        model.addAttribute("account", account);
        model.addAttribute("transactions", transactions != null ? transactions : new ArrayList<>());

        return "admin/user-details";
    }

    // ===============================
    // 💳 View All Transactions
    // ===============================
    @GetMapping("/transactions")
    public String viewTransactions(Model model, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        List<Transaction> transactions = new ArrayList<>();
        try {
            transactions = transactionRepository.findAll();
        } catch (Exception e) {
            System.out.println("Error fetching transactions: " + e.getMessage());
        }
        
        // Format dates
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        if (transactions != null) {
            for (Transaction tx : transactions) {
                if (tx.getDateTime() != null) {
                    tx.setFormattedDateTime(tx.getDateTime().format(formatter));
                }
            }
        }
        
        // Calculate totals
        double totalCredits = 0.0;
        double totalDebits = 0.0;
        
        if (transactions != null) {
            for (Transaction tx : transactions) {
                if (tx.getAmount() != null) {
                    if ("CREDIT".equals(tx.getType())) {
                        totalCredits += tx.getAmount();
                    } else if ("DEBIT".equals(tx.getType())) {
                        totalDebits += tx.getAmount();
                    }
                }
            }
        }

        model.addAttribute("transactions", transactions != null ? transactions : new ArrayList<>());
        model.addAttribute("totalCredits", totalCredits);
        model.addAttribute("totalDebits", totalDebits);

        return "admin/transactions";
    }

    // ===============================
    // 📈 Statistics
    // ===============================
    @GetMapping("/statistics")
    public String showStatistics(Model model, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

        List<Object[]> dailyTransactions = new ArrayList<>();
        List<Object[]> transactionVolume = new ArrayList<>();
        
        try {
            dailyTransactions = transactionRepository.getDailyTransactionCount(weekAgo);
        } catch (Exception e) {
            System.out.println("Error fetching daily transactions: " + e.getMessage());
        }
        
        try {
            transactionVolume = transactionRepository.getDailyTransactionVolume(weekAgo);
        } catch (Exception e) {
            System.out.println("Error fetching transaction volume: " + e.getMessage());
        }

        model.addAttribute("dailyTransactions", dailyTransactions != null ? dailyTransactions : new ArrayList<>());
        model.addAttribute("transactionVolume", transactionVolume != null ? transactionVolume : new ArrayList<>());

        return "admin/statistics";
    }

    // ===============================
    // ➕ Create Admin
    // ===============================
    @PostMapping("/create-admin")
    public String createAdmin(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session) {

        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        User existingUser = userRepository.findByUsername(username);
        if (existingUser != null) {
            return "redirect:/admin/users?error=exists";
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password); // In production, use passwordEncoder.encode(password)
        user.setRole("ADMIN");
        user.setName("Administrator");

        try {
            userRepository.save(user);
            return "redirect:/admin/users?success=created";
        } catch (Exception e) {
            System.out.println("Error creating admin: " + e.getMessage());
            return "redirect:/admin/users?error=failed";
        }
    }
}
