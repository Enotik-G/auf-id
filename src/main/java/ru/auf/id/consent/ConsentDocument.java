package ru.auf.id.consent;

/**
 * На какой документ дано согласие. Значения совпадают с {@code consents_doc_type_check} в БД:
 * добавляя сюда новое, нужна и миграция, расширяющая CHECK.
 */
public enum ConsentDocument {
    /** Согласие на обработку персональных данных (152-ФЗ) — даётся при активации учётки. */
    PERSONAL_DATA
}
