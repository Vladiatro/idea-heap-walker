package net.falsetrue.heapwalker.util.map;

import com.sun.jdi.ObjectReference;

import java.util.function.Supplier;

// @todo synchronized?
@SuppressWarnings("Duplicates")
public class ObjectMap<T> {
    private static final int INITIAL_TABLE_SIZE = 1 << 15;
    public static final int ENLARGE_PER_EIGHT = 6;

    private int tableLength = INITIAL_TABLE_SIZE;
    private Node[] table;
    private int count;

    public ObjectMap() {
        clear();
    }

    public void put(ObjectReference reference, T value) {
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
                node.value = value;
                return;
            }
            node = node.next;
        }
        table[pos] = new Node(reference, value, table[pos]);
        count++;
    }

    public void putIfAbsent(ObjectReference reference, Supplier<T> valueSupplier) {
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
                return;
            }
            node = node.next;
        }
        table[pos] = new Node(reference, valueSupplier.get(), table[pos]);
        count++;
    }

    @SuppressWarnings("unchecked")
    public T get(ObjectReference reference) {
        int pos = (int) (reference.uniqueID() % tableLength);
        Node node = table[pos];
        while (node != null) {
            if (node.id == reference.uniqueID()) {
                return (T) node.value;
            }
            node = node.next;
        }
        return null;
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

    private static class Node {
        final long id;
        final ObjectReference reference;
        Object value;
        Node next;

        Node(ObjectReference reference, Object value, Node next) {
            this.id = reference.uniqueID();
            this.reference = reference;
            this.value = value;
            this.next = next;
        }
    }
}
