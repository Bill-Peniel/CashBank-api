package com.cashbank.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "Cette adresse e-mail est déjà utilisée"),
    PHONE_ALREADY_USED(HttpStatus.CONFLICT, "Ce numéro de téléphone est déjà utilisé"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "E-mail ou mot de passe incorrect"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Ce compte est désactivé"),
    ACCOUNT_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "Trop de tentatives de connexion, réessayez plus tard"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token invalide ou expiré"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Utilisateur introuvable"),

    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "Portefeuille introuvable"),
    WALLET_FROZEN(HttpStatus.UNPROCESSABLE_CONTENT, "Le portefeuille est gelé"),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_CONTENT, "Solde insuffisant"),
    SAME_WALLET_TRANSFER(HttpStatus.UNPROCESSABLE_CONTENT, "Impossible de transférer vers son propre portefeuille"),
    AMOUNT_TOO_LOW(HttpStatus.UNPROCESSABLE_CONTENT, "Montant inférieur au minimum autorisé"),
    TRANSACTION_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "Montant supérieur au plafond par transaction"),
    DAILY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "Plafond journalier de sortie atteint"),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Transaction introuvable"),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "Requête déjà traitée ou en cours de traitement"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "La ressource a été modifiée simultanément, réessayez"),

    BENEFICIARY_NOT_FOUND(HttpStatus.NOT_FOUND, "Bénéficiaire introuvable"),
    BENEFICIARY_ALREADY_EXISTS(HttpStatus.CONFLICT, "Ce bénéficiaire existe déjà"),
    SELF_BENEFICIARY(HttpStatus.UNPROCESSABLE_CONTENT, "Vous ne pouvez pas vous ajouter comme bénéficiaire"),

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification introuvable"),

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Les données envoyées sont invalides"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Requête mal formée"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentification requise"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Accès refusé"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Ressource introuvable"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Conflit avec l'état actuel des données"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne est survenue");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
