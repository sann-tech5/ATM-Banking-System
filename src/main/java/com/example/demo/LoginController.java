package com.example.demo;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Map;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class LoginController {

    private final AccountRepository accountRepository;
    private final ATMTransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginController(
            AccountRepository accountRepository,
            ATMTransactionRepository transactionRepository,
            PasswordEncoder passwordEncoder) {

        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // LOGIN API
    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        String accountNumber = request.get("accountNumber");
        String pin = request.get("pin");

        Account account = accountRepository
                .findById(accountNumber)
                .orElse(null);

        if (account != null &&
                pin != null &&
                passwordEncoder.matches(pin, account.getPin())) {

            // Invalidate any previous session
            HttpSession oldSession = httpRequest.getSession(false);

            if (oldSession != null) {
                oldSession.invalidate();
            }

            // Create a new session
            HttpSession session = httpRequest.getSession(true);

            session.setAttribute("accountNumber", accountNumber);

            return Map.of(
                    "success", true,
                    "message", "Login successful",
                    "accountHolder", account.getAccountHolder(),
                    "balance", account.getBalance()
            );
        }

        return Map.of(
                "success", false,
                "message", "Invalid account number or PIN"
        );
    }

    // DEPOSIT API
    @PostMapping("/deposit")
    @Transactional
    public Map<String, Object> deposit(
            @RequestBody Map<String, String> request,
            HttpSession session) {

        // Check whether user is logged in
        String accountNumber =
                (String) session.getAttribute("accountNumber");

        if (accountNumber == null) {
            return Map.of(
                    "success", false,
                    "message", "Please login first"
            );
        }

        BigDecimal amount;

        try {
            amount = new BigDecimal(request.get("amount"));
        } catch (Exception e) {
            return Map.of("success", false, "message", "Invalid amount");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Map.of("success", false, "message", "Amount must be positive");
        }

        Account account = accountRepository
                .findById(accountNumber)
                .orElse(null);

        if (account == null) {
            return Map.of("success", false, "message", "Account not found");
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        ATMTransaction transaction = new ATMTransaction();
        transaction.setAccountNumber(accountNumber);
        transaction.setTransactionType("DEPOSIT");
        transaction.setAmount(amount);
        transaction.setTransactionDate(LocalDateTime.now());
        transaction.setBalanceAfter(account.getBalance());

        transactionRepository.save(transaction);

        return Map.of(
                "success", true,
                "message", "Money deposited successfully",
                "balance", account.getBalance()
        );
    }

    // WITHDRAW API
    @PostMapping("/withdraw")
    @Transactional
    public Map<String, Object> withdraw(
            @RequestBody Map<String, String> request,
            HttpSession session) {

        // Check login session
        String accountNumber =
                (String) session.getAttribute("accountNumber");

        if (accountNumber == null) {
            return Map.of(
                    "success", false,
                    "message", "Please login first"
            );
        }

        BigDecimal amount;

        try {
            amount = new BigDecimal(request.get("amount"));
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "message", "Invalid amount"
            );
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Map.of(
                    "success", false,
                    "message", "Amount must be positive"
            );
        }

        Account account = accountRepository
                .findById(accountNumber)
                .orElse(null);

        if (account == null) {
            return Map.of(
                    "success", false,
                    "message", "Account not found"
            );
        }

        if (account.getBalance().compareTo(amount) < 0) {
            return Map.of(
                    "success", false,
                    "message", "Insufficient balance"
            );
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        ATMTransaction transaction = new ATMTransaction();
        transaction.setAccountNumber(accountNumber);
        transaction.setTransactionType("WITHDRAW");
        transaction.setAmount(amount);
        transaction.setTransactionDate(LocalDateTime.now());
        transaction.setBalanceAfter(account.getBalance());

        transactionRepository.save(transaction);

        return Map.of(
                "success", true,
                "message", "Money withdrawn successfully",
                "balance", account.getBalance()
        );
    }

    // TRANSACTION HISTORY API
    @GetMapping("/transactions/{accountNumber}")
    public List<ATMTransaction> getTransactions(
            @PathVariable String accountNumber,
            HttpSession session) {

        // Get logged-in account from session
        String loggedInAccount =
                (String) session.getAttribute("accountNumber");

        // Check login and account ownership
        if (loggedInAccount == null ||
                !loggedInAccount.equals(accountNumber)) {
            return List.of();
        }

        return transactionRepository
                .findByAccountNumberOrderByTransactionDateDesc(
                        loggedInAccount
                );
    }
    /// CHANGE PIN API
    @PutMapping("/change-pin")
    @Transactional
    public Map<String, Object> changePin(
            @RequestBody Map<String, String> request,
            HttpSession session) {

        // Get logged-in account from session
        String accountNumber =
                (String) session.getAttribute("accountNumber");

        if (accountNumber == null) {
            return Map.of(
                    "success", false,
                    "message", "Please login first"
            );
        }

        String oldPin = request.get("oldPin");
        String newPin = request.get("newPin");

        Account account = accountRepository
                .findById(accountNumber)
                .orElse(null);

        if (account == null) {
            return Map.of(
                    "success", false,
                    "message", "Account not found"
            );
        }

        if (oldPin == null ||
                !passwordEncoder.matches(oldPin, account.getPin())) {
            return Map.of(
                    "success", false,
                    "message", "Incorrect old PIN"
            );
        }

        if (newPin == null || !newPin.matches("\\d{4}")) {
            return Map.of(
                    "success", false,
                    "message", "New PIN must be exactly 4 digits"
            );
        }

        if (passwordEncoder.matches(newPin, account.getPin())) {
            return Map.of(
                    "success", false,
                    "message", "New PIN must be different"
            );
        }

        account.setPin(passwordEncoder.encode(newPin));
        accountRepository.save(account);

        return Map.of(
                "success", true,
                "message", "PIN changed successfully"
        );
    }
    // LOGOUT API
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return Map.of(
                "success", true,
                "message", "Logout successful"
        );
    }
}