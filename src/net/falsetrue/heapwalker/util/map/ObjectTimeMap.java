package net.falsetrue.heapwalker.util.map;

import com.sun.jdi.ObjectReference;

/**
 * {@code long}-version of {@code ObjectMap}. Mostly duplicated, but avoids boxing/unboxing and erasure stuff.
 */
@SuppressWarnings("Duplicates")
public class ObjectTimeMap {
    private static final int INITIAL_TABLE_SIZE = 1 << 15;
    public static final int ENLARGE_PER_EIGHT = 6;

    private int tableLength = INITIAL_TABLE_SIZE;
    private Node[] table;
    private int count;

    private class Node {
        final long id;
        final ObjectReference reference;
        long time;
        Node next;

        Node(ObjectReference reference, long time, Node next) {
            this.id = reference.uniqueID();
            this.reference = reference;
            this.time = time;
            this.next = next;
        }
    }

    public void put(ObjectReference reference, long time) {
        if (reference == null) {
            return;
        }
        if (count >= tableLength * ENLARGE_PER_EIGHT / 8) {
            collect();
            if (count >= tableLength * ENLARGE_PER_EIGHT / 8) {
                enlargeTable();
            }
        }
        int pos = (int) (reference.uniqueID() % tableLength);
        Node node = table[pos];
        while (node != null) {
            if (node.id == reference.uniqueID()) {
                node.time = time;
                return;
            }
            node = node.next;
        }
        table[pos] = new Node(reference, time, table[pos]);
        count++;
    }

    public long get(ObjectReference reference) {
        int pos = (int) (reference.uniqueID() % tableLength);
        Node node = table[pos];
        while (node != null) {
            if (node.id == reference.uniqueID()) {
                return node.time;
            }
            node = node.next;
        }
        return -1;
    }

    public void clear() {
        table = new Node[tableLength];
    }

    public void collect() {
        for (int i = 0; i < table.length; i++) {
            while (table[i] != null && table[i].reference.isCollected()) {
                table[i] = table[i].next;
                count--;
            }
            if (table[i] != null) {
                for (Node node = table[i]; node.next != null; node = node.next) {
                    if (node.next.reference.isCollected()) {
                        node.next = node.next.next;
                        count--;
                    }
                }
            }
        }
    }

    private void enlargeTable() {
        tableLength *= 2;
        Node[] oldTable = table;
        table = new Node[tableLength];
        int pos;
        for (Node first : oldTable) {
            for (Node node = first; node != null; node = node.next) {
                pos = (int) (node.id % tableLength);
                node.next = table[pos];
                table[pos] = node;
            }
        }
    }
}
