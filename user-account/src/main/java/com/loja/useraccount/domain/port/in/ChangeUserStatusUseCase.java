package com.loja.useraccount.domain.port.in;

public interface ChangeUserStatusUseCase {
    void blockUser(String userId);
    void unblockUser(String userId);
}
