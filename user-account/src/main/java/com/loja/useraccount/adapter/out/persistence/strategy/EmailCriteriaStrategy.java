package com.loja.useraccount.adapter.out.persistence.strategy;

import com.loja.useraccount.application.dto.UserSearchCriteria;
import jakarta.persistence.TypedQuery;

public class EmailCriteriaStrategy implements CriteriaStrategy {

    @Override
    public boolean supports(UserSearchCriteria criteria) {
        return criteria.email() != null && !criteria.email().isBlank();
    }

    @Override
    public String conditionFragment() {
        return "u.email LIKE :email";
    }

    @Override
    public <T> void applyParameter(TypedQuery<T> query, UserSearchCriteria criteria) {
        query.setParameter("email", "%" + criteria.email() + "%");
    }
}
