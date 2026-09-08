package uk.co.ams.certplatform.application.port;

import uk.co.ams.certplatform.domain.model.Account;
import java.util.List;
import java.util.Optional;

public interface AccountRepositoryPort {
    Account save(Account account);
    Optional<Account> findById(String id);
    List<Account> findByEnvironmentId(String environmentId);
    void deleteById(String id);
    long count();
}
