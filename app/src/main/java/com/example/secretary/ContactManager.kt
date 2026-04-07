package com.example.secretary

import android.content.Context
import android.provider.ContactsContract
import android.util.Log

class ContactManager(private val context: Context) {
    private val TAG = "ContactManager"

    fun getAllContacts(): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        try {
            context.contentResolver.query(uri, projection, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val number = cursor.getString(numIdx) ?: continue
                    results.add(mapOf("name" to name, "phone" to number))
                }
            }
        } catch (e: Exception) { Log.e(TAG, "getAllContacts error", e) }
        return results.distinctBy { it["phone"]?.replace(" ","")?.replace("-","") }
    }

    fun getContactsWithEmail(): List<Map<String, String>> {
        val contacts = getAllContacts().toMutableList()
        val emailMap = mutableMapOf<String, String>()
        try {
            val uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Email.ADDRESS
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
                val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val email = cursor.getString(emailIdx) ?: continue
                    emailMap[name] = email
                }
            }
        } catch (e: Exception) { Log.e(TAG, "getEmails error", e) }
        return contacts.map { c ->
            val email = emailMap[c["name"]]
            if (email != null) c + ("email" to email) else c
        }
    }

    fun searchContact(query: String): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)
                    val number = cursor.getString(numIdx)
                    results.add(mapOf("name" to name, "phone" to number))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
        }
        return results.distinctBy { it["phone"] }
    }
}
