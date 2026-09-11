package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.application.service.AccountService;
import uk.co.ams.certplatform.domain.model.Account;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/environments/{environmentId}/accounts")
    public ResponseEntity<?> createAccount(@PathVariable String environmentId, @RequestBody CreateAccountRequest request) {
        Account created = accountService.createAccount(
            environmentId,
            request.name(),
            request.accountId(),
            request.authType(),
            request.toCredentials()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(created));
    }

    @PutMapping("/accounts/{id}")
    public ResponseEntity<AccountResponse> updateAccount(@PathVariable String id, @RequestBody UpdateAccountRequest request) {
        Account updated = accountService.updateAccount(
            id,
            request.name(),
            request.accountId(),
            request.authType(),
            request.toCredentials()
        );
        return ResponseEntity.ok(AccountResponse.from(updated));
    }

    @GetMapping("/environments/{environmentId}/accounts")
    public ResponseEntity<List<AccountResponse>> getAccountsByEnvironment(@PathVariable String environmentId) {
        List<AccountResponse> accounts = accountService.getAccountsByEnvironmentId(environmentId).stream()
                .map(AccountResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String id) {
        return accountService.getAccount(id)
                .map(AccountResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/accounts/{id}/test-connection")
    public ResponseEntity<?> testConnection(@PathVariable String id) {
        return ResponseEntity.ok(accountService.testConnection(id));
    }
}
