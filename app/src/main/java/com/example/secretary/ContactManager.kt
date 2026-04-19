package com.example.secretary

import android.content.Context
import android.provider.ContactsContract
import android.util.Log

class ContactManager(private val context: Context) {
    private val TAG = "ContactManager"

    fun getAllContacts(): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        val addressesByContactId = getPostalAddressMap()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        try {
            context.contentResolver.query(uri, projection, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val contactId = cursor.getLong(idIdx).toString()
                    val name = cursor.getString(nameIdx) ?: continue
                    val number = cursor.getString(numIdx) ?: continue
                    val contact = mutableMapOf(
                        "contact_id" to contactId,
                        "name" to name,
                        "phone" to number
                    )
                    addressesByContactId[contactId]?.let { contact["address"] = it }
                    results.add(contact)
                }
            }
        } catch (e: Exception) { Log.e(TAG, "getAllContacts error", e) }
        return results.distinctBy { it["phone"]?.replace(" ","")?.replace("-","") }
    }

    fun getContactsWithEmail(): List<Map<String, String>> {
        val contacts = getAllContacts().toMutableList()
        val emailByContactId = mutableMapOf<String, String>()
        val emailByName = mutableMapOf<String, String>()
        try {
            val uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Email.ADDRESS
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
                val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                while (cursor.moveToNext()) {
                    val contactId = cursor.getLong(idIdx).toString()
                    val name = cursor.getString(nameIdx) ?: continue
                    val email = cursor.getString(emailIdx) ?: continue
                    emailByContactId[contactId] = email
                    emailByName[name] = email
                }
            }
        } catch (e: Exception) { Log.e(TAG, "getEmails error", e) }
        return contacts.map { c ->
            val email = c["contact_id"]?.let { emailByContactId[it] } ?: emailByName[c["name"]]
            if (email != null) c + ("email" to email) else c
        }
    }

    fun searchContact(query: String): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        val addressesByContactId = getPostalAddressMap()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                
                while (cursor.moveToNext()) {
                    val contactId = cursor.getLong(idIdx).toString()
                    val name = cursor.getString(nameIdx) ?: continue
                    val number = cursor.getString(numIdx) ?: continue
                    val contact = mutableMapOf(
                        "contact_id" to contactId,
                        "name" to name,
                        "phone" to number
                    )
                    addressesByContactId[contactId]?.let { contact["address"] = it }
                    results.add(contact)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
        }
        return results.distinctBy { it["phone"] }
    }

    private fun getPostalAddressMap(): Map<String, String> {
        val addresses = mutableMapOf<String, String>()
        val uri = ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID,
            ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
            ContactsContract.CommonDataKinds.StructuredPostal.STREET,
            ContactsContract.CommonDataKinds.StructuredPostal.CITY,
            ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,
            ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY
        )
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID)
                val formattedIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
                val streetIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.STREET)
                val cityIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.CITY)
                val postcodeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE)
                val countryIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY)
                while (cursor.moveToNext()) {
                    val contactId = cursor.getLong(idIdx).toString()
                    val formatted = cursor.getString(formattedIdx)
                    val address = formatted?.cleanAddress()
                        ?: listOf(
                            cursor.getString(streetIdx),
                            cursor.getString(cityIdx),
                            cursor.getString(postcodeIdx),
                            cursor.getString(countryIdx)
                        ).joinAddressParts()
                    if (address.isNotBlank()) addresses.putIfAbsent(contactId, address)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPostalAddressMap error", e)
        }
        return addresses
    }

    private fun String.cleanAddress(): String =
        split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(", ")

    private fun List<String?>.joinAddressParts(): String =
        mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .joinToString(", ")
}
