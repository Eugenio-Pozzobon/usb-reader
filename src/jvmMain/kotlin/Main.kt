@file:OptIn(DelicateCoroutinesApi::class)

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.*
import java.awt.Desktop
import java.io.*
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.JFileChooser
import kotlin.io.path.exists
import kotlin.io.path.inputStream

@OptIn(ExperimentalFoundationApi::class)
fun main(): Unit = application {


    val commPorts = remember { mutableStateOf(0) }
    lateinit var comPort: SerialPort
    lateinit var comPortInputStream: InputStream
    lateinit var comPortOutputStream: OutputStream
    lateinit var bufferR: BufferedReader

    var serialLineRead = ""
    val runningExtraction = remember { mutableStateOf(false) }
    val statusConexao = remember { mutableStateOf(false) }
    val configWindow = remember { mutableStateOf(false) }

    val currentLogId = remember { mutableStateOf(0)}
    val tempLogFile = "./log_temp.csv"
    var newLogFile = File(tempLogFile)

    var lastSavedLogId = 0
    var saveTime = 0
    lateinit var saveFolder:String

    suspend fun getSensorData() = coroutineScope {
        withContext(Dispatchers.IO) {
            comPortOutputStream.write(115)
            Thread.sleep(500)
        }
        serialLineRead = withContext(Dispatchers.IO) {
            bufferR.readLine()
        }
        println(serialLineRead)
    }

    suspend fun getInfo() = coroutineScope {
        withContext(Dispatchers.IO) {
            comPortOutputStream.write(105)
            Thread.sleep(500)
            do{
                serialLineRead = bufferR.readLine()
            }while(serialLineRead.split(",").size != 2)
            println(serialLineRead)
        }
        try {
                lastSavedLogId = serialLineRead.split(",")[0].toInt()
                saveTime = serialLineRead.split(",")[1].toInt()
                print("lastSavedLogId: ")
                println(lastSavedLogId)
                print("saveTime: ")
                println(saveTime)
        }catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun virgulaToPontoeVirgula(logs: String) {
        val path: Path = Paths.get(logs)
        val charset: Charset = StandardCharsets.UTF_8

        var content = String(Files.readAllBytes(path), charset)
        content = content.replace(',', ';')
        Files.write(path, content.toByteArray(charset))
    }

    fun pontoToVirgula(logs: String) {
        val path: Path = Paths.get(logs)
        val charset: Charset = StandardCharsets.UTF_8

        var content = String(Files.readAllBytes(path), charset)
        content = content.replace('.', ',')
        Files.write(path, content.toByteArray(charset))
    }


    suspend fun saveDataFiles() = coroutineScope {
        withContext(Dispatchers.IO) {
            Thread.sleep(500)
        }
        do {

            runningExtraction.value = true


            withContext(Dispatchers.IO) {
                if(!Files.exists(Paths.get(saveFolder + "/" + comPort.systemPortName))) {
                    Files.createDirectory(Paths.get(saveFolder + "/" + comPort.systemPortName))
                }
            }

            serialLineRead = withContext(Dispatchers.IO) {
                bufferR.readLine()
            }

            val headerInfo = serialLineRead.split(',')

            if (headerInfo.size == 4) {

                if (newLogFile.path.drop(2) != tempLogFile.drop(2)) {

                    if (newLogFile.length() > 35) {
                        virgulaToPontoeVirgula(newLogFile.path)
                        pontoToVirgula(newLogFile.path)
                    } else {
                        print("DELETANDO ARQUIVO ")
                        println(newLogFile.path)
                        withContext(Dispatchers.IO) {
                            Files.delete(Paths.get(newLogFile.path))
                        }
                    }
                }

                currentLogId.value = headerInfo[3].toInt()


                val current = LocalDateTime.now()
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                val formattedDateTime = current.format(formatter)

                newLogFile = File(saveFolder +"/" + comPort.systemPortName + "/" + formattedDateTime + "_log-id-" + currentLogId.value + ".csv")
                newLogFile.writeText("t, temp1, temp2\n")
            } else if (headerInfo.size == 3) {
                newLogFile.appendText(serialLineRead.plus("\n"))
            }
        } while (serialLineRead != "Fim do Arquivo" && statusConexao.value)

        runningExtraction.value = false

        if (newLogFile.length() > 35) {
            virgulaToPontoeVirgula(newLogFile.path)
            pontoToVirgula(newLogFile.path)
        } else {
            print("DELETANDO ARQUIVO ")
            println(newLogFile.path)
            withContext(Dispatchers.IO) {
                Files.delete(Paths.get(newLogFile.path))
            }
        }

        withContext(Dispatchers.IO) {
            comPortOutputStream.write(100)
            Desktop.getDesktop().open(File(saveFolder))
        }
    }


    suspend fun desconnectSerial() = coroutineScope {
        comPort.closePort()
        statusConexao.value = false
        runningExtraction.value = false
        exitApplication()
    }

    suspend fun connectSerial(comName: String) = coroutineScope {
        comPort = SerialPort.getCommPort(comName)
        comPort.baudRate = 115200
        comPort.openPort()
        comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0)
        comPortInputStream = comPort.inputStream
        comPortOutputStream = comPort.outputStream
        try {
            bufferR = BufferedReader(withContext(Dispatchers.IO) {
                InputStreamReader(comPortInputStream, "UTF-8")
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
        withContext(Dispatchers.IO) {
            Thread.sleep(1000)
        }
        do {
            serialLineRead = withContext(Dispatchers.IO) {
                bufferR.readLine()
            }
        } while (serialLineRead != " - Starting - ")
        getInfo()
        println("Conectado!")
        statusConexao.value = true

    }

    val colorTheme = Color(23,79,89)
    val btAppColor = ButtonDefaults.buttonColors(backgroundColor = colorTheme, contentColor = Color.White)



    Window(
        onCloseRequest = ::exitApplication,
        title = "NTC Logger Utilities",
        icon = loadIcon(),
        state = rememberWindowState(width = 400.dp, height = 300.dp),
        resizable = false

    ) {
        commPorts.value = SerialPort.getCommPorts().count()
        MaterialTheme {

            if (statusConexao.value) {
                IconButton(modifier = Modifier.size(38.dp).padding(8.dp), // padding,
                    onClick = {
                        configWindow.value = !configWindow.value
                    }) {
                    Icon(
                        if (!configWindow.value) Icons.Rounded.Settings else Icons.Rounded.ArrowBack, contentDescription = "Configuração"
                    )
                }
            }

            Column(Modifier.fillMaxSize() ,
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                if(configWindow.value){
                    Row(Modifier.padding(16.dp)) {
                        val textState = remember { mutableStateOf((saveTime/1000).toString()) }
                        TextField(
                            value = textState.value,
                            onValueChange = { textState.value = it },
                            label = { Text("Período de amostragem (s)") } ,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        IconButton(modifier = Modifier.size(50.dp).padding(8.dp), // padding,
                            onClick = {
                                GlobalScope.launch(){
                                    withContext(Dispatchers.IO) {
                                        comPortOutputStream.write(104)
                                        comPortOutputStream.write(textState.value.toInt())
                                    }
                                }
                            }) {
                            Icon(
                                Icons.Rounded.Refresh, contentDescription = "Configuração"
                            )
                        }
                    }

                    Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colorTheme),
                                border = BorderStroke(1.dp, colorTheme),
                    onClick = {
                        comPortOutputStream.write(100)
                    }) {
                        Text("Excluir Dados")
                    }

//                    Button(modifier = Modifier.align(Alignment.CenterHorizontally),
//                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorTheme),
//                        border = BorderStroke(1.dp, colorTheme),
//                    onClick = {
//                        comPortOutputStream.write(100)
//                    }) {
//                        Text("Resetar Sistema")
//                    }
                }else {
                    if (statusConexao.value) {
                        Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorTheme),
                            border = BorderStroke(1.dp, colorTheme),
                            onClick = {
                                GlobalScope.launch {
                                    desconnectSerial()
                                }
                            }) {
                            Text("Desconectar")
                        }

                    } else {

                        Text(
                            "Selecione uma porta\nUSB para conectar:\n",
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        LazyVerticalGrid(
                            cells = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.fillMaxSize(fraction = 0.8F)
                        ) {
                            items(commPorts.value) {
                                Button(
                                    colors = btAppColor,
                                    modifier = Modifier.wrapContentWidth(), onClick = {
                                        commPorts.value = SerialPort.getCommPorts().count()
                                        if (!statusConexao.value && SerialPort.getCommPorts()
                                                .count() == commPorts.value
                                        ) {
                                            GlobalScope.launch {
                                                connectSerial(SerialPort.getCommPorts()[it].systemPortName.toString())
                                            }
                                        } else {
                                            commPorts.value = SerialPort.getCommPorts().count()
                                        }
                                    }) {
                                    Text(SerialPort.getCommPorts()[it].systemPortName.toString())
                                }
                            }
                        }

                    }
                    if (statusConexao.value) {
                        Button(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            colors = btAppColor, enabled = !runningExtraction.value && statusConexao.value,
                            onClick = {
                                val fc = JFileChooser()

                                fc.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY;
                                val returnVal = fc.showOpenDialog(null)

                                if (returnVal == JFileChooser.APPROVE_OPTION) {
                                    saveFolder = fc.selectedFile.absolutePath;
                                    //This is where a real application would open the file.

                                    GlobalScope.launch {
                                        getInfo()
                                        withContext(Dispatchers.IO) {
                                            comPortOutputStream.write(112)
                                            saveDataFiles()
                                        }
                                    }

                                } else {
                                    println("Open command cancelled by user.");
                                }
                            },
                        ) {
                            Text(if (!runningExtraction.value) "Extrair Dados" else "...extraindo...")
                        }
                    }

                    if (runningExtraction.value && statusConexao.value) {
                        Text(
                            "log " + (currentLogId.value) + "/" + (lastSavedLogId),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }


//                Button(modifier = Modifier.align(Alignment.CenterHorizontally),
//                onClick = {
//                    comPortOutputStream.write(104)
//                    comPortOutputStream.write(60)
//                }) {
//                Text("Configurar")

            }

        }
    }
}

fun loadIcon(): Painter? {
    // app.dir is set when packaged to point at our collected inputs.
    val iconPath = Paths.get("./app.ico")
    return if (iconPath.exists()) {
        BitmapPainter(iconPath.inputStream().buffered().use { loadImageBitmap(it) })
    } else {
        null
    }
}