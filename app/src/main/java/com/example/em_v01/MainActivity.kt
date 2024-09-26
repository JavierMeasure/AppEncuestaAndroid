package com.example.em_v01

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Leer metadata e iterar sobre ella, creando botones

        data class Datos(val contenido: String, val nombre_csv: String,val headers:Array<String>)

        val jsonString = readRawResource(R.raw.metadata)

        // Parsear el JSON en una lista de Datos
        val gson = Gson()
        val datosListType = object : TypeToken<List<Datos>>() {}.type
        val datosList: List<Datos> = gson.fromJson(jsonString, datosListType)

        // Iterar sobre la lista de datos y crear botones dinámicamente
        datosList.forEach { datos ->
            crear_boton(datos.contenido, datos.nombre_csv,datos.headers)
        }

        val linearLayout = findViewById<LinearLayout>(R.id.Contenedor_opciones)

        val archivos = fileList()
        for (archivo in archivos) {
            if(archivo.contains(".csv")){
                val dynamicButton = Button(this).apply {
                    var dummy = archivo.substring(1)
                    text = "Enviar $dummy"
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener{
                        enviar(archivo)
                    }
                }

                linearLayout.addView(dynamicButton)
            }
        }


    }

    private fun crear_boton(contenido:String,nombre_csv:String,headers:Array<String>){

        val linearLayout = findViewById<LinearLayout>(R.id.Contenedor_opciones)


        val dynamicButton = Button(this).apply {
            text = contenido
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener{
                //Enviar a la otra activity el nombre_csv


                val intent = Intent(this@MainActivity,Encuesta::class.java).apply {
                    putExtra("ARCHIVO",nombre_csv)
                    putExtra("HEADERS",headers)
                }
                startActivity(intent)
            }
        }

        linearLayout.addView(dynamicButton)
    }

    private fun readRawResource(resourceId: Int): String {
        val inputStream = resources.openRawResource(resourceId)
        val reader = InputStreamReader(inputStream)
        return reader.readText().also {
            reader.close()
        }
    }

    private fun enviar(nombre:String){

        val fileName = nombre
        val file = File(this.filesDir, fileName)

        if (file.exists()) {
            // Paso 2: Obtener el URI del archivo utilizando FileProvider
            val fileUri: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )

            // Paso 3: Crear un Intent para compartir el archivo
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"  // Especifica el tipo MIME del archivo (puede ser "text/csv" para CSV)
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)  // Otorgar permisos de lectura al destinatario
            }

            // Paso 4: Mostrar el selector para elegir la aplicación para compartir
            startActivity(Intent.createChooser(shareIntent, "Compartir archivo usando"))
        } else {
            println("No funciono la wea")
        }
    }

}