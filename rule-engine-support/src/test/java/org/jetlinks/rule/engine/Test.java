package org.jetlinks.rule.engine;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortPacketListener;
import lombok.SneakyThrows;
import reactor.core.publisher.Flux;

import java.time.Duration;

public class Test {
    @SneakyThrows
    public static void main(String[] args) {

        SerialPort[] ports = SerialPort.getCommPorts();

        for (SerialPort port : ports) {
            System.out.println(port);
        }
        SerialPort port = ports[6];
        System.out.println(port);

        port.addDataListener(new SerialPortPacketListener() {
            @Override
            public int getPacketSize() {
                return 9;
            }

            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                byte[] data = event.getReceivedData();

                System.out.println("温度" + Integer.parseInt(data[3]+""+data[4],16)/10F);
                System.out.println("湿度" + Integer.parseInt(data[5]+""+data[6],16)/10F);
            }
        });
        port.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY, true);
        port.openPort();

        Flux.interval(Duration.ofSeconds(0), Duration.ofSeconds(1))
                .subscribe(r -> {
                    port.writeBytes(new byte[]{
                            0x01 ,0x05 ,0x00 ,0x00 ,0x55 ,0x00 ,(byte) 0xF2 ,(byte)0x9A
                            //0x01, 0x04, 0x00, 0x01, 0x00, 0x02, 0x20, 0x0B
                    }, 8);
                });
        Thread.sleep(100000);
    }
}
