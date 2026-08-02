package com.loja.useraccount.application.dto;

import com.loja.useraccount.domain.model.UserStatus;

/** Search criteria for paginated user listing. */
public record UserSearchCriteria(String email, UserStatus status, String sortBy) {
}
