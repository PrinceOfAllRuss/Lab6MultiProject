package tools

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import tools.input.Input
import tools.input.InputFile
import tools.result.Result
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import multilib.server.commandsData.ServerCommandsData
import multilib.utilities.commandsData.*
import multilib.utilities.serializ.Serializer


class CommandProcessor: KoinComponent {

    private val commandsList: CommandsList by inject()
    private val clientList: DataList by inject()

    fun process(input: Input) {

        var result: Result? = Result()
        var mapData: Map<String, String?>
        val serializer = Serializer()

        var command = ""
        var receiveCommandsData = ClientCommandsData() //получаемые от клиента данные

        //инициализирую все, что требуется для передачи данных
        var port = 1313
        var host: InetAddress
        val serverSocket = DatagramSocket(port)
        val receivingDataBuffer = ByteArray(65535)
        var sendingDataBuffer = ByteArray(65535)
        val inputPacket = DatagramPacket(receivingDataBuffer, receivingDataBuffer.size)
        var outputPacket: DatagramPacket
        var receivedData = ""

        var xml = ""

        val commandsData = ServerCommandsData() //список команд с требуемыми параметрами, который отправляется клиенту
        val xmlCommands = serializer.serialize(commandsData)
        sendingDataBuffer = xmlCommands.toByteArray()

        while ( true ) {
            serverSocket.receive(inputPacket)
            port = inputPacket.port
            host = inputPacket.address

            if (clientList.getAddressList().size == 0 ||
                !clientList.getAddressList().contains(port.toString() + host.toString())) {

                clientList.getAddressList().add(port.toString() + host.toString())

                outputPacket = DatagramPacket(sendingDataBuffer, sendingDataBuffer.size, host, port)
                serverSocket.send(outputPacket) // отправляю список команд клиенту

                input.outMsg("Client connected\n")

                continue
            }

            xml = String(inputPacket.data, 0, inputPacket.length)
            receiveCommandsData = serializer.deserialize(xml)

            command = receiveCommandsData.getName()

            input.outMsg("Client send command: " + command + "\n")

            result?.setMessage("")

            if ( !commandsList.containsCommand(command) ) {
                result!!.setMessage("This command does not exist\n")
            }
            else {
                try {
                    mapData = receiveCommandsData.getMapData()
                    result = commandsList.getCommand(command)?.action(mapData)

                } catch ( e: NumberFormatException ) {
                    input.outMsg("Wrong data\n")
                    if ( input.javaClass == InputFile("").javaClass ) {
                        continue
                    }
                } catch ( e: NullPointerException ) {
                    input.outMsg("Not all data entered\n")
                }
            }

            xml = serializer.serialize(result)
            sendingDataBuffer = xml.toByteArray()

            outputPacket = DatagramPacket(sendingDataBuffer, sendingDataBuffer.size, host, port)
            serverSocket.send(outputPacket)

            if (result?.getExit() == true) {
                clientList.getAddressList().remove(port.toString() + host.toString())
                sendingDataBuffer = xmlCommands.toByteArray()
                result.setExit(false)
            }

        }

    }
}