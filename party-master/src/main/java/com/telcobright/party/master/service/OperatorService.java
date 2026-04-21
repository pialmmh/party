package com.telcobright.party.master.service;

import com.telcobright.party.master.entity.Operator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class OperatorService {

    public List<Operator> list() { return Operator.listAll(); }

    public Operator findById(Long id) {
        Operator op = Operator.findById(id);
        if (op == null) throw new NotFoundException("operator " + id + " not found");
        return op;
    }

    @Transactional
    public Operator create(Operator op) {
        if (op.status == null) op.status = "ACTIVE";
        op.persist();
        return op;
    }

    @Transactional
    public Operator update(Long id, Operator patch) {
        Operator op = findById(id);
        if (patch.shortName != null) op.shortName = patch.shortName;
        if (patch.fullName != null) op.fullName = patch.fullName;
        if (patch.operatorType != null) op.operatorType = patch.operatorType;
        if (patch.companyName != null) op.companyName = patch.companyName;
        if (patch.address1 != null) op.address1 = patch.address1;
        if (patch.city != null) op.city = patch.city;
        if (patch.country != null) op.country = patch.country;
        if (patch.phone != null) op.phone = patch.phone;
        if (patch.email != null) op.email = patch.email;
        if (patch.status != null) op.status = patch.status;
        return op;
    }

    @Transactional
    public void delete(Long id) {
        Operator op = findById(id);
        op.status = "DELETED";
    }
}
