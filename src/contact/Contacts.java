/*
 * Copyright (c) 2014 Swen Walkowski.
 * All rights reserved. Originator: Swen Walkowski.
 * Get more information about CardDAVSyncOutlook at http://sourceforge.net/projects/carddavsyncoutlook
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package contact;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import main.Status;

public class Contacts {

    public enum Addressbook {
        WEBDAVADDRESSBOOK,
        OUTLOOKADDRESSBOOK
    }

    private final HashMap<String, Contact> davContacts;
    private final HashMap<String, Contact> outlookContacts;
    private final List<String> listSyncContacts;

    /**
     * Constructor
     */
    public Contacts(String strWorkingDir) {
        davContacts = new HashMap();
        outlookContacts = new HashMap();
        listSyncContacts = new ArrayList();

        this.loadUidsFromFile(strWorkingDir);
    }

    /**
     * Private
     */
    private void loadUidsFromFile(String strWorkingDir) {
        Status.print("Load last Sync UIDs");
        File file = new File((strWorkingDir + "lastSync.txt"));

        if (file.exists()) {
            try (BufferedReader in = new BufferedReader(
                    new FileReader(strWorkingDir + "lastSync.txt"))) {
                String line;
                while ((line = in.readLine()) != null) {
                    this.listSyncContacts.add(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Public
     */
    public Integer numberOfContacts(Addressbook whichAdressbook) {
        int size = 0;
        switch (whichAdressbook) {
            case WEBDAVADDRESSBOOK:
                size = davContacts.size();
                break;
            case OUTLOOKADDRESSBOOK:
                size = outlookContacts.size();
                break;
        }

        return size;
    }

    public void addContact(Addressbook whichAdressbook, Contact conContact) {
        switch (whichAdressbook) {
            case WEBDAVADDRESSBOOK:
                davContacts.put(conContact.getUid(), conContact);
                break;
            case OUTLOOKADDRESSBOOK:
                outlookContacts.put(conContact.getUid(), conContact);
                break;
        }
    }

    public void removeContact(Addressbook whichAdressbook, String strUidKey) {
        switch (whichAdressbook) {
            case WEBDAVADDRESSBOOK:
                davContacts.get(strUidKey).deleteTmpContactPictureFile();
                davContacts.remove(strUidKey);
                break;
            case OUTLOOKADDRESSBOOK:
                outlookContacts.get(strUidKey).deleteTmpContactPictureFile();
                outlookContacts.remove(strUidKey);
                break;
        }
    }

    public HashMap<String, Contact> getAddressbook(Addressbook whichAdressbook) {
        switch (whichAdressbook) {
            case WEBDAVADDRESSBOOK:
                return davContacts;
            case OUTLOOKADDRESSBOOK:
                return outlookContacts;
        }

        return null;
    }

    public Contact getContact(Addressbook whichAdressbook, String strUidSearchContact) {
        switch (whichAdressbook) {
            case WEBDAVADDRESSBOOK:
                return davContacts.get(strUidSearchContact);
            case OUTLOOKADDRESSBOOK:
                return outlookContacts.get(strUidSearchContact);
            default:
                return null;
        }
    }

    public void saveUidsToFile(String strWorkingDir) {
        File file = new File(strWorkingDir + "lastSync.txt");
        FileWriter writer;
        try {
            writer = new FileWriter(file);

            for (Entry<String, Contact> entry : davContacts.entrySet()) {
                writer.write(entry.getValue().getUid());
                writer.write(System.getProperty("line.separator"));
            }

            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteTmpContactPictures() {
        Iterator<Entry<String, Contact>> iter = davContacts.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String, Contact> entry = iter.next();
            entry.getValue().deleteTmpContactPictureFile();

        }

        iter = outlookContacts.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String, Contact> entry = iter.next();
            entry.getValue().deleteTmpContactPictureFile();

        }
    }

    public void compareAddressBooks(boolean initMode) {
        if (initMode)
            this.compareAddressBooksByFields();
        else
            this.compareAddressBooksByUID();
    }

    private void compareAddressBooksByUID() {
        // look for contacts in both address books that were present during last
        // sync and were deleted in one address book. Mark them as
        // "to delete" in the other one
        for (String currentUID : listSyncContacts){
            if (davContacts.get(currentUID) == null) {
                if (outlookContacts.get(currentUID) != null) {
                    // case 1.1: deleted contact in dav
                    outlookContacts.get(currentUID).setStatus(Contact.Status.DELETE);
                }
            }

            if (outlookContacts.get(currentUID) == null) {
                if (davContacts.get(currentUID) != null) {
                    // case 1.2: deleted contact in outlook
                    davContacts.get(currentUID).setStatus(Contact.Status.DELETE);
                }
            }
        }

        List<Contact> newOutlookContacts = new ArrayList();
        List<Contact> newDAVContacts = new ArrayList();
        List<Contact> replacedOutlookContacts = new ArrayList();
        List<Contact> replacedlDAVContacts = new ArrayList();

        //Leading Outlook
        for (Entry<String, Contact> outlookEntry : outlookContacts.entrySet()) {
            Contact outlookContact = outlookEntry.getValue();
            String outlookKey = outlookEntry.getKey();

            if (outlookContact.getStatus() != Contact.Status.READIN &&
                    outlookContact.getStatus() != Contact.Status.UIDADDED) {
                // already handled
                continue;
            }

            Contact davContact = davContacts.get(outlookKey);
            if (davContact == null) {
                // case 2.1
                // corresponding dav contact does not exist, insert it
                Contact newContact = new Contact(outlookContact, Contact.Status.NEW);
                newDAVContacts.add(newContact);
                continue;
            }

            if (outlookContact.equalTo(davContact)) {
                // case 4: both contacts are equal, nothing to do
                outlookContact.setStatus(Contact.Status.UNCHANGED);
                davContact.setStatus(Contact.Status.UNCHANGED);
                continue;
            }

            if (outlookContact.getLastModificationTime().getTime() >
                    davContact.getLastModificationTime().getTime()) {
                // case 3.1
                // outlook contact is newer then dav contact, dav contact will
                // be replaced
                replacedlDAVContacts.add(davContact);

                Contact newContact = new Contact(outlookContact, Contact.Status.CHANGED);
                newDAVContacts.add(newContact);

                outlookContact.setStatus(Contact.Status.UNCHANGED);
                davContact.setStatus(Contact.Status.DELETE);
            } else {
                // case 3.2
                // dav contact is newer then outlook contact, outlook contact will
                // be replaced
                replacedOutlookContacts.add(outlookContact);

                Contact newContact = new Contact(davContact, Contact.Status.CHANGED);
                newContact.setEntryID(outlookContact.getEntryID());
                newOutlookContacts.add(newContact);

                davContact.setStatus(Contact.Status.UNCHANGED);
                outlookContact.setStatus(Contact.Status.DELETE);
            }
        }

        // apply changes saved in temporary lists
        for (Contact contact : replacedOutlookContacts) {
            this.removeContact(Addressbook.OUTLOOKADDRESSBOOK, contact.getUid());
        }

        for (Contact contact : replacedlDAVContacts) {
            this.removeContact(Addressbook.WEBDAVADDRESSBOOK, contact.getUid());
        }
        for (Contact contact : newDAVContacts) {
            this.addContact(Contacts.Addressbook.WEBDAVADDRESSBOOK, contact);
        }

        //Leading WebDav
        for (Entry<String, Contact> davEntry : davContacts.entrySet()) {
            Contact davContact = davEntry.getValue();
            String davKey = davEntry.getKey();

            if (davContact.getStatus() != Contact.Status.READIN &&
                    (davContact.getStatus() != Contact.Status.UIDADDED)) {
                // already handled
                continue;
            }

            Contact outlookContact = outlookContacts.get(davKey);

            if (outlookContact == null) {
                // case 2.2
                // corresponding outlook contact does not exist, insert it
                Contact newContact = new Contact(davContact, Contact.Status.NEW);
                newOutlookContacts.add(newContact);
            }
            // all other cases already handled
        }

        for (Contact contact : newOutlookContacts) {
            this.addContact(Contacts.Addressbook.OUTLOOKADDRESSBOOK, contact);
        }
    }

    /** Compare contacts by comparing all fields rather than UIDs.
     * When all fields match, copy the UID of the DAV contact to outlook.
     */
    private void compareAddressBooksByFields() {

        List<Contact> newOutlookContacts = new ArrayList();
        List<Contact> replacedOutlookContacts = new ArrayList();

        for (Contact outlookContact : outlookContacts.values()) {

            if (outlookContact.getStatus() != Contact.Status.UIDADDED) {
                // not a new contact, better skip
                continue;
            }

            for (Contact davContact: davContacts.values()) {
                if (outlookContact.equalTo(davContact)) {
                    Contact newContact = new Contact(outlookContact, Contact.Status.UIDADDED, davContact.getUid());
                    newOutlookContacts.add(newContact);
                    replacedOutlookContacts.add(outlookContact);
                    break;
                }
            }
        }

        // apply changes saved in temporary lists
        for (Contact contact : replacedOutlookContacts) {
            this.removeContact(Addressbook.OUTLOOKADDRESSBOOK, contact.getUid());
        }
        for (Contact contact : newOutlookContacts) {
            this.addContact(Contacts.Addressbook.OUTLOOKADDRESSBOOK, contact);
        }
    }

    public void printStatus() {
        Status.print("Outlook Contacts: ");
        this.print(outlookContacts);
        Status.print("DAV Contacs: ");
        this.print(davContacts);;
    }

    private void print(HashMap<String, Contact> addressBook) {
       for (Contact contact : addressBook.values()) {
           String s = contact.getFirstName() + "," +
                   contact.getLastName() + ": " +
                   contact.getStatus();
           Status.print(s);
       }
    }
}
