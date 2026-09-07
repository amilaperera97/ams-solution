package com.example.certplatform.application.port;

import com.example.certplatform.domain.model.Account;
import java.util.List;
import java.util.Optional;

public interface AccountRepositoryPort {
    Account save(Account account);
    Optional<Account> findById(String id);
    List<Account> findByEnvironmentId(String environmentId);
    void deleteById(String id);
    long count();
}
