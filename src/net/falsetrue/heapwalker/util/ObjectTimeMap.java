package net.falsetrue.heapwalker.util;

import com.sun.jdi.ObjectReference;

public class ObjectTimeMap {
    private static final int TABLE_SIZE = 32771;

    private Node[] table;

    public ObjectTimeMap() {
        table = new Node[TABLE_SIZE];
    }

    private class Node {
        final long id;
        long time;
        Node next;

        Node(long id, long time, Node next) {
            this.id = id;
            this.time = time;
            this.next = next;
        }
    }

    public void put(ObjectReference reference, long time) {
        int pos = (int) (reference.uniqueID() % TABLE_SIZE);
        Node node = table[pos];
        while (node != null) {
            if (node.id == reference.uniqueID()) {
                node.time = time;
                return;
            }
            node = node.next;
        }
        table[pos] = new Node(reference.uniqueID(), time, table[pos]);
    }

    public long get(ObjectReference reference) {
        int pos = (int) (reference.uniqueID() % TABLE_SIZE);
        Node node = table[pos];
        while (node != null) {
            if (node.id == reference.uniqueID()) {
                return node.time;
            }
            node = node.next;
        }
        return -1;
    }
}
