package com.mulechat.app.util;

import com.mulechat.app.model.Contact;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory contact list for the UI shell. Resets when the process dies --
 * there is no local encrypted storage wired up yet (see plan section 10,
 * SQLDelight + SQLCipher). This exists purely so ContactsFragment and
 * ConversationActivity can share the same list within one app session.
 */
public class ContactRepository {
    private static final ContactRepository INSTANCE = new ContactRepository();
    private final List<Contact> contacts = new ArrayList<>();

    public static ContactRepository getInstance() {
        return INSTANCE;
    }

    private ContactRepository() {}

    public List<Contact> getAll() {
        return contacts;
    }

    public void add(Contact contact) {
        contacts.add(contact);
    }
}
