package com.figlms.identity.internal;

import com.figlms.identity.api.UserAccount;
import com.figlms.identity.api.UserDirectory;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
class DefaultUserDirectory implements UserDirectory {

    private final AccountRepository accounts;

    DefaultUserDirectory(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return username == null ? Optional.empty() : accounts.findByUsername(username);
    }

    @Override
    public Optional<UserAccount> findById(long id) {
        return accounts.findById(id);
    }
}
