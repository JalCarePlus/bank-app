package banking_app.controller;

import banking_app.entity.Account;
import banking_app.entity.User;
import banking_app.entity.Transaction;
import banking_app.repository.AccountRepository;
import banking_app.repository.UserRepository;
import banking_app.repository.TransactionRepository;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }
}
@Controller
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // ================== Registration ==================
    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@ModelAttribute User user, Model model) {
        // Check if username already exists
        if (userRepository.findByUsername(user.getUsername()) != null) {
            model.addAttribute("error", "Username already exists!");
            return "register";
        }
        
        user.setRole("USER");
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user.setActive(true);
        userRepository.save(user);

        // Create account automatically with initial balance
        Account account = new Account();
        account.setUser(user);
        account.setBalance(1000.0);
        account.setAccountNumber(generateAccountNumber());
        accountRepository.save(account);

        model.addAttribute("message", "Registration successful! Your Account Number: " + account.getAccountNumber());
        return "register";
    }

    private String generateAccountNumber() {
        long number = 1000000000L + (long) (Math.random() * 8999999999L);
        return String.valueOf(number);
    }

    // ================== Login ==================
    @GetMapping("/login")
    public String showLoginForm(Model model) {
        model.addAttribute("user", new User());
        return "login";
    }

    @PostMapping("/login")
    public String loginUser(@ModelAttribute("user") User user, 
                           Model model, 
                           HttpSession session) {
        
        // Find user by username
        User existingUser = userRepository.findByUsername(user.getUsername());
        
        // Check if user exists and password matches
        if (existingUser != null && 
            passwordEncoder.matches(user.getPassword(), existingUser.getPassword())) {
            
            // Update last login time
            existingUser.setLastLoginAt(LocalDateTime.now());
            userRepository.save(existingUser);
            
            // Store in session
            session.setAttribute("loggedInUser", existingUser);

            // Redirect based on role
            if ("ADMIN".equals(existingUser.getRole())) {
                return "redirect:/admin/dashboard";
            } else {
                Account account = accountRepository.findByUser(existingUser);
                model.addAttribute("user", existingUser);
                model.addAttribute("account", account);
                return "dashboard";
            }
        } else {
            // Invalid credentials
            model.addAttribute("error", "Invalid username or password");
            return "login";
        }
    }

    // ================== Dashboard ==================
    @GetMapping("/dashboard")
    public String showDashboard(Model model, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        
        Account account = accountRepository.findByUser(loggedInUser);
        model.addAttribute("user", loggedInUser);
        model.addAttribute("account", account);
        return "dashboard";
    }

    // ================== Transfer Money ==================
    @GetMapping("/transfer")
    public String showTransferPage(Model model, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        
        Account account = accountRepository.findByUser(loggedInUser);
        model.addAttribute("user", loggedInUser);
        model.addAttribute("account", account);
        return "transfer";
    }
    
    @Transactional
    @PostMapping("/transfer")
    public String transferMoney(@RequestParam("receiverAccount") String receiverAccountNumber,
                                @RequestParam("amount") Double amount,
                                Model model,
                                HttpSession session) {

        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        Account senderAccount = accountRepository.findByUser(loggedInUser);
        Account receiverAccount = accountRepository.findByAccountNumber(receiverAccountNumber);

        if (receiverAccount == null) {
            model.addAttribute("message", "Receiver account not found!");
            model.addAttribute("account", senderAccount);
            model.addAttribute("user", loggedInUser);
            return "transfer";
        }

        if (senderAccount.getBalance() < amount) {
            model.addAttribute("message", "Insufficient balance!");
            model.addAttribute("account", senderAccount);
            model.addAttribute("user", loggedInUser);
            return "transfer";
        }

        // Perform transfer
        senderAccount.setBalance(senderAccount.getBalance() - amount);
        receiverAccount.setBalance(receiverAccount.getBalance() + amount);
        accountRepository.save(senderAccount);
        accountRepository.save(receiverAccount);

        // Record transactions
        Transaction debitTx = new Transaction();
        debitTx.setUser(loggedInUser);
        debitTx.setAmount(amount);
        debitTx.setType("DEBIT");
        debitTx.setReceiverAccount(receiverAccount.getAccountNumber());
        debitTx.setDateTime(LocalDateTime.now());
        transactionRepository.save(debitTx);

        Transaction creditTx = new Transaction();
        creditTx.setUser(receiverAccount.getUser());
        creditTx.setAmount(amount);
        creditTx.setType("CREDIT");
        creditTx.setReceiverAccount(senderAccount.getAccountNumber());
        creditTx.setDateTime(LocalDateTime.now());
        transactionRepository.save(creditTx);

        model.addAttribute("message", "Transferred ₹" + amount + " to " + receiverAccountNumber);
        model.addAttribute("account", senderAccount);
        model.addAttribute("user", loggedInUser);

        return "transfer";
    }

    // ================== Transaction History ==================
    @GetMapping("/transactions")
    public String showTransactions(Model model, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        
        List<Transaction> transactions = transactionRepository.findByUser(loggedInUser);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");
        
        // Calculate totals in Java, not in Thymeleaf
        double totalCredits = 0.0;
        double totalDebits = 0.0;
        
        if (transactions != null && !transactions.isEmpty()) {
            for (Transaction tx : transactions) {
                if (tx.getDateTime() != null) {
                    tx.setFormattedDateTime(tx.getDateTime().format(formatter));
                }
                
                // Calculate totals
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
        model.addAttribute("transactionCount", transactions != null ? transactions.size() : 0);
        
        return "transactions";
    }

    // ================== Logout ==================
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    // ================== Create Admin (Temporary Method) ==================
    @GetMapping("/force-create-admin")
    @ResponseBody
    public String forceCreateAdmin() {
        try {
            // Delete existing admin if any
            User existingAdmin = userRepository.findByUsername("admin");
            if (existingAdmin != null) {
                userRepository.delete(existingAdmin);
            }
            
            // Create new admin
            User admin = new User();
            admin.setName("Administrator");
            admin.setEmail("admin@banking.com");
            admin.setUsername("admin");
            
            // Encode password properly
            String rawPassword = "admin123";
            String encodedPassword = passwordEncoder.encode(rawPassword);
            admin.setPassword(encodedPassword);
            
            admin.setRole("ADMIN");
            admin.setActive(true);
            admin.setCreatedAt(LocalDateTime.now());
            
            userRepository.save(admin);
            
            // Verify the password works
            boolean verification = passwordEncoder.matches("admin123", admin.getPassword());
            
            return "<html><body style='font-family: Arial; padding: 20px;'>" +
                   "<h2 style='color: green;'>✅ Admin Created!</h2>" +
                   "<p><strong>Username:</strong> admin</p>" +
                   "<p><strong>Password:</strong> admin123</p>" +
                   "<p><strong>Password verification:</strong> " + 
                   (verification ? "✅ SUCCESS" : "❌ FAILED") + "</p>" +
                   "<p><strong>Encoded password:</strong> " + encodedPassword + "</p>" +
                   "<p><a href='/login' style='background: #28a745; color: white; padding: 10px 20px; " +
                   "text-decoration: none; border-radius: 5px;'>Go to Login</a></p>" +
                   "</body></html>";
                   
        } catch (Exception e) {
            return "<h2 style='color: red;'>Error: " + e.getMessage() + "</h2>";
        }
    }
}
