package com.telcobright.party.master.service;

import com.telcobright.party.domain.OperatorRole;
import com.telcobright.party.domain.UserStatus;
import com.telcobright.party.master.entity.OperatorUser;
import com.telcobright.party.master.security.PasswordHasher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class OperatorUserService {

    @Inject PasswordHasher hasher;

    public List<OperatorUser> list() { return OperatorUser.listAll(); }

    public OperatorUser findById(Long id) {
        OperatorUser u = OperatorUser.findById(id);
        if (u == null) throw new NotFoundException("operator user " + id + " not found");
        return u;
    }

    public OperatorUser findByEmail(String email) {
        return OperatorUser.find("email", email).firstResult();
    }

    @Transactional
    public OperatorUser create(String email, String password, String firstName, String lastName,
                               OperatorRole role, Long operatorId) {
        if (OperatorUser.find("email", email).firstResult() != null) {
            throw new BadRequestException("email already registered");
        }
        if (role == OperatorRole.OPERATOR_ADMIN && operatorId == null) {
            throw new BadRequestException("OPERATOR_ADMIN requires operatorId");
        }
        OperatorUser u = new OperatorUser();
        u.email = email;
        u.passwordHash = hasher.hash(password);
        u.firstName = firstName;
        u.lastName = lastName;
        u.role = role;
        u.operatorId = operatorId;
        u.status = UserStatus.ACTIVE;
        u.persist();
        return u;
    }

    @Transactional
    public OperatorUser resetPassword(Long id, String newPassword) {
        OperatorUser u = findById(id);
        u.passwordHash = hasher.hash(newPassword);
        return u;
    }

    @Transactional
    public OperatorUser setStatus(Long id, UserStatus status) {
        OperatorUser u = findById(id);
        u.status = status;
        return u;
    }

    @Transactional
    public void delete(Long id) {
        OperatorUser u = findById(id);
        u.status = UserStatus.DELETED;
    }
}
