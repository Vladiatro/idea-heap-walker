package net.falsetrue.heapwalker;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ClassesTableModel extends AbstractTableModel {
    private List<Row> rows = new ArrayList<>();

    public void clear() {
        rows.clear();
    }

    public void add(String className, Long count) {
        rows.add(new Row(className, count));
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public String getColumnName(int column) {
        switch (column) {
            case 0:
                return "Class";
            case 1:
                return "Count";
        }
        throw new RuntimeException();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return rows.get(rowIndex).getValue(columnIndex);
    }

    public class Row {
        String className;
        Long count;

        public Row(String className, Long count) {
            this.className = className;
            this.count = count;
        }

        private Object getValue(int columnIndex) {
            switch (columnIndex) {
                case 0:
                    return className;
                case 1:
                    return count;
            }
            throw new RuntimeException();
        }
    }
}
