package com.example.mensajes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException
import android.content.Intent
import android.app.Activity
import android.net.Uri
import android.widget.ArrayAdapter
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var editTextMessage: EditText
    private lateinit var editTextTime: EditText
    private lateinit var buttonConfirm: Button
    private lateinit var buttonUpload: Button
    private lateinit var listViewPhoneNumbers: ListView

    private val phoneNumbersList: ArrayList<String> = ArrayList()

    private var isSendingSMS = false
    private var currentIndex = 0
    private var intervalInSeconds = 0L

    companion object {
        private const val PERMISSION_REQUEST_READ_STORAGE = 456
        private const val PERMISSION_REQUEST_SMS = 123
        private const val PICK_FILE_REQUEST_CODE = 789
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editTextMessage = findViewById(R.id.editTextMessage)
        editTextTime = findViewById(R.id.editTextTime)
        buttonConfirm = findViewById(R.id.buttonConfirm)
        buttonUpload = findViewById(R.id.buttonUpload)
        listViewPhoneNumbers = findViewById(R.id.listViewPhoneNumbers)

        buttonConfirm.setOnClickListener {
            val message = editTextMessage.text.toString()

            if (phoneNumbersList.isNotEmpty() && message.isNotEmpty()) {
                if (!isSendingSMS) {
                    val timeInSeconds = editTextTime.text.toString().toLongOrNull()
                    if (timeInSeconds != null && timeInSeconds > 0) {
                        intervalInSeconds = timeInSeconds
                        currentIndex = 0
                        startSendingSMS(message)
                    } else {
                        Toast.makeText(this, "Ingrese un intervalo de tiempo válido", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    stopSendingSMS()
                }
            } else {
                Toast.makeText(
                    this,
                    "Ingrese al menos un número de teléfono y un mensaje",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        buttonUpload.setOnClickListener {
            openFilePicker()
        }

        checkStoragePermission()
    }

    private fun checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_READ_STORAGE
            )
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/json"
        startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val json = readJsonFromUri(uri)
                if (json != null) {
                    val gson = Gson()
                    val contacts = gson.fromJson(json, Contacts::class.java)

                    phoneNumbersList.clear()
                    phoneNumbersList.addAll(contacts.numeros_telefono)

                    val adapter = ArrayAdapter<String>(
                        this,
                        android.R.layout.simple_list_item_1,
                        phoneNumbersList
                    )
                    listViewPhoneNumbers.adapter = adapter
                } else {
                    Toast.makeText(this, "Error al leer el archivo JSON", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun readJsonFromUri(uri: Uri): String? {
        val contentResolver = contentResolver
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.use {
                it.readText()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun startSendingSMS(message: String) {
        isSendingSMS = true
        buttonConfirm.text = "Detener"

        val job = Job()
        val scope = CoroutineScope(Dispatchers.Main + job)
        scope.launch {
            while (isSendingSMS && currentIndex < phoneNumbersList.size) {
                val phoneNumber = phoneNumbersList[currentIndex]
                sendSMS(phoneNumber, message)
                currentIndex++
                delay(intervalInSeconds * 1000)
            }

            stopSendingSMS()
        }
    }

    private fun stopSendingSMS() {
        isSendingSMS = false
        buttonConfirm.text = "Enviar Mensaje"
    }

    private fun sendSMS(phoneNumber: String, message: String) {
        val smsManager: SmsManager = SmsManager.getDefault()

        try {
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Toast.makeText(this, "Mensaje enviado a $phoneNumber", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al enviar mensaje a $phoneNumber", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_READ_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, do nothing
                } else {
                    Toast.makeText(
                        this,
                        "Permiso denegado para acceder a la memoria interna",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            PERMISSION_REQUEST_SMS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    val message = editTextMessage.text.toString()
                    startSendingSMS(message)
                } else {
                    Toast.makeText(
                        this,
                        "Permiso denegado para enviar mensajes",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    data class Contacts(val numeros_telefono: List<String>)
}
