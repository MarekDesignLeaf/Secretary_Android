package com.example.secretary

import android.content.Context
import android.provider.ContactsContract
import android.util.Log

class ContactManager(private val context: Context) {
    private val TAG = "ContactManager"

    private data class PostalAddress(
        val formatted: String,
        val line1: String?,
        val city: String?,
        val postcode: String?,
        val country: String?
    )

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
                    addressesByContactId[contactId]?.let { contact.addPostalAddress(it) }
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
                    addressesByContactId[contactId]?.let { contact.addPostalAddress(it) }
                    results.add(contact)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
        }
        return results.distinctBy { it["phone"] }
    }

    private fun getPostalAddressMap(): Map<String, PostalAddress> {
        val addresses = mutableMapOf<String, PostalAddress>()
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
                    val line1 = cursor.getOptionalString(streetIdx)
                    val city = cursor.getOptionalString(cityIdx)
                    val postcode = cursor.getOptionalString(postcodeIdx)
                    val country = cursor.getOptionalString(countryIdx)
                    val formatted = cursor.getOptionalString(formattedIdx)?.cleanAddress()
                        ?: listOf(line1, city, postcode, country).joinAddressParts()
                    if (formatted.isNotBlank()) {
                        addresses.putIfAbsent(
                            contactId,
                            PostalAddress(
                                formatted = formatted,
                                line1 = line1,
                                city = city,
                                postcode = postcode,
                                country = country
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPostalAddressMap error", e)
        }
        return addresses
    }

    private fun MutableMap<String, String>.addPostalAddress(address: PostalAddress) {
        this["address"] = address.formatted
        address.line1?.let {
            this["address_line1"] = it
            this["billing_address_line1"] = it
        }
        address.city?.let {
            this["city"] = it
            this["billing_city"] = it
        }
        address.postcode?.let {
            this["postcode"] = it
            this["billing_postcode"] = it
        }
        address.country?.let {
            this["country"] = it
            this["billing_country"] = it
        }
    }

    private fun android.database.Cursor.getOptionalString(index: Int): String? =
        if (index >= 0) getString(index)?.trim()?.takeIf(String::isNotBlank) else null

    private fun String.cleanAddress(): String =
        split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(", ")

    private fun List<String?>.joinAddressParts(): String =
        mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .joinToString(", ")
}
