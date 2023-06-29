package com.example.mensajes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.view.View
import android.widget.ArrayAdapter
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
import kotlinx.coroutines.*
import java.text.Normalizer
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter

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
            checkMessagePermission()
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

    private fun checkMessagePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                PERMISSION_REQUEST_SMS
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
            val parts = smsManager.divideMessage(message)
            val sentIntents = ArrayList<PendingIntent>()
            val deliveryIntents = ArrayList<PendingIntent>()

            for (i in 0 until parts.size) {
                sentIntents.add(PendingIntent.getBroadcast(this, 0, Intent("SMS_SENT"), 0))
                deliveryIntents.add(PendingIntent.getBroadcast(this, 0, Intent("SMS_DELIVERED"), 0))
            }

            // Registrar el receptor de difusión para el evento de envío
            val sentReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (resultCode == Activity.RESULT_OK) {
                        updateListView(phoneNumber, true)
                        Toast.makeText(context, "Mensaje enviado correctamente", Toast.LENGTH_SHORT).show()
                    } else {
                        updateListView(phoneNumber, false)
//                        Toast.makeText(context, "Error al enviar el mensaje", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            registerReceiver(sentReceiver, IntentFilter("SMS_SENT"))

            // Registrar el receptor de difusión para el evento de entrega
            val deliveredReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (resultCode == Activity.RESULT_OK) {
                        updateListView(phoneNumber, true)
//                        Toast.makeText(context, "Mensaje entregado correctamente", Toast.LENGTH_SHORT).show()
                    } else {
                        updateListView(phoneNumber, false)
//                        Toast.makeText(context, "Error al entregar el mensaje", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            registerReceiver(deliveredReceiver, IntentFilter("SMS_DELIVERED"))

            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, deliveryIntents)
        } catch (e: Exception) {
            e.printStackTrace()
            formatPhoneNumber(phoneNumber, false)
        }
    }

    private fun updateListView(phoneNumber: String, isSent: Boolean) {
        val adapter = listViewPhoneNumbers.adapter as ArrayAdapter<String>
        val index = phoneNumbersList.indexOf(phoneNumber)
        if (index != -1) {
            phoneNumbersList.removeAt(index)
            phoneNumbersList.add(index, formatPhoneNumber(phoneNumber, isSent))
            adapter.notifyDataSetChanged()
        }
    }

    private fun formatPhoneNumber(phoneNumber: String, isSent: Boolean): String {
        val status = if (isSent) "Enviado" else "Error"
        return "$phoneNumber - $status"
    }
}

data class Contacts(val numeros_telefono: List<String>)
