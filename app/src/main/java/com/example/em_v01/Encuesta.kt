package com.example.em_v01

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.sql.Timestamp
import java.util.UUID
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

class Encuesta : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.encuesta)

        println("Inicio Encuesta")

        val headers = intent.getStringArrayExtra("HEADERS")
        val nombreCsv = intent.getStringExtra("ARCHIVO")
        var jsonString = ""

        if(nombreCsv == "rally24g"){
            jsonString = readRawResource(R.raw.rally24g)
        }
        if(nombreCsv == "rally24nl"){
            jsonString = readRawResource(R.raw.rally24nl)
        }

        val uniqueID = UUID.randomUUID().toString()
        val prefix = "EET" // Encuesta en Terreno
        val combinedID = "$prefix-$uniqueID"

        val respuesta: MutableMap<String, String> = mutableMapOf(
            "ID" to combinedID,
            "inicioEncuesta" to "",
            "finEncuesta" to ""
        )

        if (headers != null) {
            for (elemento in headers) {
                respuesta[elemento] = ""
                if(elemento.contains("gasto")){
                    respuesta[elemento] = "-1"
                }
            }
        }

        println(respuesta)



        //Crear clase respuesta y medir cuando inicio encuesta

        data class Preguntas(
            val ID: String,
            val style: String,
            val name: String,
            val type: String,
            val unique: String,
            val question: String,
            val answer: String,
            val next: String
        )

        var timestamp = Timestamp(System.currentTimeMillis())
        respuesta["inicioEncuesta"] = timestamp.toString()

        println("Creacion de map para guardar respuestas")

        //Leer csv y guardarlo en memoria


        println("--------------------------")
        println("R$nombreCsv.csv")
        println("--------------------------")

        println("Obtenido datos desde la otra activity")

        // Crear archivo CSV si es que no existe.

        val keysList: List<String> = respuesta.keys.toList()


        println("Obtenidos datos desde json")

        createFileIfNotExists(this,"R$nombreCsv.csv",keysList)

        println("Creado archivo en caso de que no exista")

        // Parsear el JSON en una lista de Datos
        val gson = Gson()
        val datosListType = object : TypeToken<List<Preguntas>>() {}.type
        val datosList: List<Preguntas> = gson.fromJson(jsonString, datosListType)

        println("Json con preguntas parseado y se inicia iteracion")

        //Iterar sobre las preguntas

        var pregunta:Preguntas = datosList[0]
        var ref_pregunta:List<Int>
        val contenedor_pregunta = findViewById<LinearLayout>(R.id.question)
        ref_pregunta = generar_pregunta(pregunta.name,pregunta.type,pregunta.unique,pregunta.question,pregunta.answer)

        val ref_button = crear_boton()
        val boton = findViewById<Button>(ref_button)
        var resultado = ""
        var next_id = -1
        val file_csv = File(this.filesDir, "R$nombreCsv.csv")

        boton.setOnClickListener{
            //Capturar Respuesta

            resultado = ""

            resultado = when{

                pregunta.type == "texto" && (pregunta.answer == "country" || pregunta.answer == "comunas") -> capturar_autocomplete(ref_pregunta[0])
                pregunta.unique == "multiple" -> capturar_multiple(ref_pregunta)
                pregunta.answer == "blank" || pregunta.type == "text_num" -> capturar_blank(ref_pregunta[0])
                pregunta.type == "texto" -> capturarRespuestaUnica(ref_pregunta[0])
                pregunta.type == "numero" -> capturar_dropdown(ref_pregunta[0])
                else -> ""
            }

            if(resultado != ""){

                respuesta[pregunta.name] = resultado
                    .replace(",","*")
                    .replace("\n","[")

                //Borrar pregunta anterior.
                contenedor_pregunta.removeAllViews()
                resultado = ""

                //Next ID y final de encuesta, solo cuando se tenga una respuesta no vacia.
                next_id = next_id(pregunta.next,respuesta)
                println(next_id)
                if(next_id != -1){
                    //Generar pregunta siguiente
                    pregunta = datosList[next_id]
                    ref_pregunta = generar_pregunta(pregunta.name,pregunta.type,pregunta.unique,pregunta.question,pregunta.answer)
                    //Fin generacion pregunta

                }
                else{
                    //Fin de encuesta
                    println("Fin Encuesta")
                    timestamp = Timestamp(System.currentTimeMillis())
                    respuesta["finEncuesta"] = timestamp.toString()
                    addHeaderToFile(file_csv, respuesta.values.toList())
                    val intent = Intent(this@Encuesta,MainActivity::class.java)
                    startActivity(intent)
                }
            }
        }
    }

    private fun createFileIfNotExists(context: Context, fileName: String, header: List<String>) {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) {
            try {
                val created = file.createNewFile()
                if (created) {
                    println("Archivo creado exitosamente.")
                    // Agregar el encabezado al archivo después de crearlo
                    addHeaderToFile(file, header)
                } else {
                    println("No se pudo crear el archivo.")
                }
            } catch (e: IOException) {
                e.printStackTrace()
                println("Error al crear el archivo: ${e.message}")
            }
        } else {
            println("El archivo ya existe.")
        }
    }

    private fun addHeaderToFile(file: File, header: List<String>) {
        try {
            FileWriter(file, true).use { writer ->
                val headerLine = header.joinToString(separator = ",") + "\n"
                writer.write(headerLine)
            }
            println("Encabezado agregado exitosamente.")
        } catch (e: IOException) {
            e.printStackTrace()
            println("Error al agregar el encabezado: ${e.message}")
        }
    }

    private fun readRawResource(resourceId: Int): String {
        val inputStream = resources.openRawResource(resourceId)
        val reader = InputStreamReader(inputStream)
        return reader.readText().also {
            reader.close()
        }
    }

    private fun generar_pregunta(name:String,type:String,unique:String,question:String,answer:String):List<Int>{

        val textView = TextView(this)

        textView.setText(question)
        textView.setTextSize(20F)
        textView.setPadding(16, 16, 16, 16)
        textView.setTextColor(Color.BLACK)
        var id_pregunta:List<Int> = listOf(0)

        val linearLayout = findViewById<LinearLayout>(R.id.question)
        linearLayout.addView(textView)


        if (unique == "unique"){
            if(type == "texto"){
                when(answer){
                    "comunas" -> id_pregunta = listOf(generar_autocomplete(leer_txt(this,"comunas"),linearLayout))
                    "country" -> id_pregunta = listOf(generar_autocomplete(leer_txt(this,"country"),linearLayout))
                    "blank" -> id_pregunta =  listOf(respuesta_blanca(0,linearLayout))
                    else -> id_pregunta = listOf(respuesta_unica(separar_palabra(answer),linearLayout))
                }
            }
            if(type == "numero"){
                 id_pregunta = listOf(generar_dropdown(lista_numeros(answer),linearLayout))
            }
            if(type == "text_num"){
                id_pregunta = listOf(respuesta_blanca(1,linearLayout))
            }
        }
        if(unique == "multiple"){
            id_pregunta = respuesta_multiple(separar_palabra(answer),linearLayout)
        }
        return id_pregunta
    }

    fun leer_txt(context: Context, answer:String): Array<String> {
        // Abre el archivo desde la carpeta raw
        var inputStream: InputStream? = null
        if(answer == "comunas"){
            inputStream = context.resources.openRawResource(R.raw.comunas)
        }
        if(answer == "country"){
            inputStream = context.resources.openRawResource(R.raw.country)
        }

        val reader = BufferedReader(InputStreamReader(inputStream))

        // Lee cada línea y guárdala en una lista
        val lineas = mutableListOf<String>()
        println("Abriendo funcion")
        reader.useLines { lines -> lines.forEach { lineas.add(it) } }

        return lineas.toTypedArray()
    }

    /*private fun generar_autocomplete(respuestas: Array<String>, ly: LinearLayout):Int{
        val autoCompleteTextView = AutoCompleteTextView(this).apply {
            id = View.generateViewId()
            hint = "Escribe tu respuesta aquí"
            // Crear y asignar un ArrayAdapter con las opciones para el autocompletado
            val adapter = ArrayAdapter(this@Encuesta, android.R.layout.simple_dropdown_item_1line, respuestas)
            setAdapter(adapter)
            threshold = 1 // Número mínimo de caracteres para mostrar sugerencias
            setTextColor(Color.BLACK)
        }
        ly.addView(autoCompleteTextView)
        return autoCompleteTextView.id
    }*/

    private fun generar_autocomplete(respuestas: Array<String>, ly: LinearLayout): Int {
        val autoCompleteTextView = AutoCompleteTextView(this).apply {
            id = View.generateViewId()
            hint = "Escribe tu respuesta aquí"
            // Crear y asignar un ArrayAdapter con las opciones para el autocompletado
            val adapter = ArrayAdapter(this@Encuesta, android.R.layout.simple_dropdown_item_1line, respuestas)
            setAdapter(adapter)
            threshold = 1 // Número mínimo de caracteres para mostrar sugerencias
            setTextColor(Color.BLACK)

            // Detectar cuando se presiona "Enter" y cerrar el teclado
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                    // Ocultar el teclado
                    hideKeyboard(this)
                    true // Consumir el evento para que no haga salto de línea
                } else {
                    false // Permitir otros eventos
                }
            }

            // Asegurarse de que "Enter" sea tratado como "Done"
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        ly.addView(autoCompleteTextView)
        return autoCompleteTextView.id
    }

    // Función para ocultar el teclado
    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }


    private fun capturar_autocomplete(ref:Int):String{
        val autoCompleteTextView = findViewById<AutoCompleteTextView>(ref)
        val respuesta = autoCompleteTextView.text.toString()

        return respuesta
    }

    private fun generar_dropdown(respuestas:Array<String>,ly:LinearLayout):Int{
        val spinner = Spinner(this).apply {
            id = View.generateViewId() // Asigna un ID único al Spinner

        }

        val adapter = ArrayAdapter(this,android.R.layout.simple_spinner_item,respuestas).apply{
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = adapter

        ly.addView(spinner)
        return spinner.id
    }

    private fun capturar_dropdown(ref:Int):String{
        val spinner = findViewById<Spinner>(ref)
        return spinner.selectedItem.toString()
    }

    private fun lista_numeros(numeros:String):Array<String>{
        val (start, end) = numeros.split(":").map { it.toInt() }

        // Generar el array de números del rango especificado
        val numbersArray = (start..end).map { it.toString() }.toTypedArray()

        // Imprimir el array para verificar el resultado
        return numbersArray
    }

    /*private fun respuesta_blanca(tipo:Int,ly:LinearLayout):Int{

        val editText = EditText(this).apply {
            id = View.generateViewId()
            hint = "Escribe tu respuesta aquí"
            setTextColor(Color.BLACK)
            if(tipo == 1){
                inputType = InputType.TYPE_CLASS_NUMBER
            }
        }
        ly.addView(editText)
        return editText.id
    }*/

    private fun respuesta_blanca(tipo: Int, ly: LinearLayout): Int {
        val editText = EditText(this).apply {
            id = View.generateViewId()
            hint = "Escribe tu respuesta aquí"
            setTextColor(Color.BLACK)
            if (tipo == 1) {
                inputType = InputType.TYPE_CLASS_NUMBER

                // Agregar TextWatcher para formato con separador de miles
                addTextChangedListener(object : TextWatcher {
                    private var current = ""

                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                        // Nada que hacer aquí
                    }

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        // Nada que hacer aquí
                    }

                    override fun afterTextChanged(s: Editable?) {
                        if (s.toString() != current) {
                            removeTextChangedListener(this) // Evitar recursión infinita

                            val cleanString = s.toString().replace("[,.]".toRegex(), "") // Remover separadores existentes
                            if (cleanString.isNotEmpty()) {
                                val parsed = cleanString.toLong()
                                val formatted = String.format("%,d", parsed) // Aplicar separador de miles
                                current = formatted
                                setText(formatted)
                                setSelection(formatted.length) // Mover el cursor al final del texto
                            }

                            addTextChangedListener(this) // Restaurar el listener
                        }
                    }
                })
            }
        }

        ly.addView(editText)
        return editText.id
    }



    private fun capturar_blank(ref:Int):String{
        val editText = findViewById<EditText>(ref)
        return editText.text.toString()
    }

    private fun respuesta_unica(respuestas: Array<String>, ly: LinearLayout): Int{
        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            id = View.generateViewId()
        }

        // Generar dinámicamente los RadioButton y agregarlos al RadioGroup
        for (opcion in respuestas) {
            val radioButton = RadioButton(this).apply {
                text = opcion
                id = View.generateViewId() // Generar un ID único para cada RadioButton
                setTextColor(Color.BLACK)
            }
            radioGroup.addView(radioButton)
        }

        // Agregar el RadioGroup al LinearLayout
        ly.addView(radioGroup)
        return radioGroup.id
    }

    private fun capturarRespuestaUnica(ref:Int):String {

        val radioGroup = findViewById<RadioGroup>(ref)

        // Obtiene el ID del RadioButton seleccionado
        val seleccionId = radioGroup.checkedRadioButtonId

        // Verifica si hay una selección
        if (seleccionId != -1) {
            // Encuentra el RadioButton seleccionado y obtiene su texto
            return findViewById<RadioButton>(seleccionId).text.toString()
        }
        return ""
    }

    private fun respuesta_multiple(respuestas: Array<String>, ly: LinearLayout):List<Int>{

        val checkBoxIds = mutableListOf<Int>()

        for (opcion in respuestas) {
            val checkBox = CheckBox(this).apply {
                text = opcion
                id = View.generateViewId() // Generar un ID único para cada CheckBox
                setTextColor(Color.BLACK)
            }
            ly.addView(checkBox)
            checkBoxIds.add(checkBox.id)
        }
        return checkBoxIds
    }

    private fun capturar_multiple(ref:List<Int>):String{

        val respuestasSeleccionadas = mutableListOf<String>()

        for (id in ref) {
            val checkBox = findViewById<CheckBox>(id)
            if (checkBox.isChecked) {
                respuestasSeleccionadas.add(checkBox.text.toString()) // Agregar la respuesta seleccionada a la lista
            }
        }
        return respuestasSeleccionadas.joinToString(separator = "&")
    }

    private fun separar_palabra(texto:String):Array<String>{
        val palabrasArray = texto.split('\n').toTypedArray()

        // Imprimir el array resultante
        return palabrasArray
    }

    private fun next_id(texto:String?,respuesta: MutableMap<String, String>):Int{
        if (texto == null){
            return -1
        }
        val pattern = "IF\\s+([^:]+):\\[(.*?)]\\s+([^\\s]+)\\s+ELSE\\s+([^\\s]+)".toRegex()
        val matchResult = pattern.find(texto)

        println("Aqui Entra")
        println(matchResult)

        return if (matchResult != null) {
            var (pregunta, comparador, valorTrue, valorFalse) = matchResult.destructured
            println("----------------------------")
            println(pregunta)
            println(comparador)
            println(respuesta[pregunta])
            println(valorTrue)
            println(valorFalse)
            comparador = comparador.replace(",","*")
            println("----------------------------------------------")
            if (comparador.contains(respuesta[pregunta].toString())) {
                valorTrue.toInt()
            } else {
                valorFalse.toInt()
            }
        } else {
            texto.toInt()
        }
    }

    private fun crear_boton(): Int{

        val linearLayout = findViewById<LinearLayout>(R.id.boton_supremo)


        val dynamicButton = Button(this).apply {
            text = "Siguiente"
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        linearLayout.addView(dynamicButton)
        return dynamicButton.id
    }
}
