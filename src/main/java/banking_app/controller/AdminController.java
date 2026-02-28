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
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;  // This was missing

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
        List<Account> allAccounts = accountRepository.findAll();
        double totalBalance = allAccounts.stream()
            .mapToDouble(Account::getBalance)
            .sum();

        // Get recent transactions (last 10)
        List<Transaction> recentTransactions = transactionRepository.findTop10ByOrderByDateTimeDesc();
        
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
        model.addAttribute("recentTransactions", recentTransactions != null ? recentTransactions : List.of());

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

        if (keyword != null && !keyword.isEmpty()) {
            userPage = userRepository
                    .findByUsernameContainingOrEmailContainingOrNameContaining(
                            keyword, keyword, keyword, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        // Get accounts for each user
        Map<Long, Account> userAccounts = new HashMap<>();
        for (User user : userPage.getContent()) {
            Account account = accountRepository.findByUser(user);
            if (account != null) {
                userAccounts.put(user.getId(), account);
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

        Account account = accountRepository.findByUser(user);
        List<Transaction> transactions = transactionRepository.findByUser(user);
        
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
        model.addAttribute("transactions", transactions != null ? transactions : List.of());

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

        List<Transaction> transactions = transactionRepository.findAll();
        
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
                if ("CREDIT".equals(tx.getType())) {
                    totalCredits += tx.getAmount();
                } else if ("DEBIT".equals(tx.getType())) {
                    totalDebits += tx.getAmount();
                }
            }
        }

        model.addAttribute("transactions", transactions != null ? transactions : List.of());
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

        List<Object[]> dailyTransactions = transactionRepository.getDailyTransactionCount(weekAgo);
        List<Object[]> transactionVolume = transactionRepository.getDailyTransactionVolume(weekAgo);

        model.addAttribute("dailyTransactions", dailyTransactions != null ? dailyTransactions : List.of());
        model.addAttribute("transactionVolume", transactionVolume != null ? transactionVolume : List.of());

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

        if (userRepository.findByUsername(username) != null) {
            return "redirect:/admin/users?error=exists";
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password); // In production, encode this
        user.setRole("ADMIN");
        user.setName("Administrator"); // Add default name

        userRepository.save(user);

        return "redirect:/admin/users?success=created";
    }
}
