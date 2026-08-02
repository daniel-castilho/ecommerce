package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.application.dto.UserSearchCriteria;
import com.loja.useraccount.domain.model.User;

/** Input port (admin-only): paginated listing of all users. */
public interface ListUsersUseCase {
    PageResult<User> listUsers(int page, int pageSize, UserSearchCriteria criteria);
}
