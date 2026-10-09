package com.cashbank.wallet.dto;

import jakarta.validation.constraints.NotNull;

import com.cashbank.wallet.WalletStatus;

public record UpdateWalletStatusRequest(@NotNull WalletStatus status) {
}
