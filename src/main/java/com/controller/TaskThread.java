package com.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.model.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class TaskThread extends Thread {

    BlockingQueue<String> IOQueue = null;
    List<Player> players = new ArrayList<>();
    List<Socket> sockets = new ArrayList<>();
    List<Enermy> enermies = new ArrayList<>();
    List<Bullet> bullets = new ArrayList<>();
    List<Item> items = new ArrayList<>();
    int ready = 0;
    int numPlayer = 0;
    boolean isStart = false;
    ObjectMapper mapper = new ObjectMapper();

    public TaskThread(BlockingQueue<String> IOQueue) {
        this.IOQueue = IOQueue;
    }

    @Override
    public void run() {
        Clock clock = Clock.systemDefaultZone();
        long cycle = clock.millis();
        for (; ; ) {
            try {
                String data = IOQueue.poll(4, TimeUnit.MILLISECONDS);
                if (data != null) handle(data);
                long curr = clock.millis();
                if (curr - cycle >= 50) {
                    try {
                        sendData();
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                    //empty queue
                    if (isStart && ready == 0) break;
                    update();
                    cycle = curr;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void update() {
        System.out.println("Size" + IOQueue.size());
        IOQueue.clear();
    }

    public void addPlayer(Socket socket, String name) {
        Player player = new Player(name, true);
        players.add(player);
        sockets.add(socket);
        numPlayer++;
    }

    public void handle(String message) {
        String[] data = message.split("\\|");
        if (data.length > 0) {
            if (data[0].equals("MOVE")) {
                int x = Integer.parseInt(data[1]);
                int y = Integer.parseInt(data[2]);
                int z = Integer.parseInt(data[3]);
                String name = data[4];
                for (Player player : players)
                    if (player.getName().equals(name)) {
                        if (player.getPlane() != null)
                            player.getPlane().setPosition(new Position(x, y, z));
                        else player.setPlane(new Plane(new Position(x, y, z)));
                        break;
                    }
            } else if (data[0].equals("START")) {
                ready++;
            } else if (data[0].equals("ENERMY")) {
                enermies.add(new Enermy(Integer.parseInt(data[1]), Integer.parseInt(data[2]), Integer.parseInt(data[4])));
                //TODO
                //add score player
            } else if (data[0].equals("ITEM")) {
                items.add(new Item(Integer.parseInt(data[1]), Integer.parseInt(data[2]), 0));
                //TODO
                //add score player
            } else if (data[0].equals("BULLET")) {
                bullets.add(new Bullet(Integer.parseInt(data[1]), new Position(Integer.parseInt(data[2]), Integer.parseInt(data[3]), Integer.parseInt(data[4])), data[5], Integer.parseInt(data[6])));
            } else if (data[0].equals("ENDGAME")) {
                ready--;
                //remove player
                int index = 0;
                for (Player player : players) {
                    if (player.equals(data[1])) break;
                    index++;
                }
                players.remove(index);
                //remove socket
                try {
                    sockets.get(index).close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                sockets.remove(index);
            }
        }
    }

    public void sendData() throws JsonProcessingException {
        List<String> jsonString = new ArrayList<>();
        String[] a = new String[Constant.NUM_OF_PLAYER];
        String[] b = new String[enermies.size()];
        String[] c = new String[bullets.size()];
        String[] d = new String[items.size()];
        //execute
        if (!isStart && ready == Constant.NUM_OF_PLAYER) jsonString.add("START");
        int i = 0;
        for (Player player : players) a[i++] = mapper.writeValueAsString(player);
        jsonString.add(Arrays.toString(a));//player
        i = 0;
        for (Enermy enermy : enermies) b[i++] = mapper.writeValueAsString(enermy);
        enermies.clear();
        jsonString.add(Arrays.toString(b));//enermy
        i = 0;
        for (Bullet bullet : bullets) c[i++] = mapper.writeValueAsString(bullet);
        bullets.clear();
        jsonString.add(Arrays.toString(c));//bullet
        i = 0;
        for (Item item : items) d[i++] = mapper.writeValueAsString(item);
        jsonString.add(Arrays.toString(d));//item
        for (Socket socket : sockets) {
            try {
                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                outputStream.writeBytes("[" + String.join(",", jsonString) + "]\n");
            } catch (IOException e) {
                System.out.println("Send data error");
                e.printStackTrace();
            }
        }
    }
}
