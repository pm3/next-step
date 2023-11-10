package io.aston;

import io.aston.worker.LinkedTree;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

public class TestStream {

    private LinkedTree<String, String> stream() {
        LinkedTree<String, String> stream = new LinkedTree<>();
        stream.add("a", "a1");
        stream.add("b", "b1");
        stream.add("c", "c1");
        stream.add("c", "c2");
        stream.add("c", "c3");
        stream.add("d", "d1");
        stream.add("e", "e1");
        return stream;
    }

    private LinkedTree<String, String> stream(BiFunction<String, String, Boolean> cleanFilterFn) {
        LinkedTree<String, String> stream = new LinkedTree<>() {
            @Override
            public boolean cleanFilterFn(String key, String value) {
                return cleanFilterFn.apply(key, value);
            }
        };
        stream.add("a", "a1");
        stream.add("b", "b1");
        stream.add("c", "c1");
        stream.add("d", "d1");
        stream.add("e", "e1");
        return stream;
    }

    @Test
    public void testAdd() {
        LinkedTree<String, String> stream = stream();
        List<String> values = stream.values();
        Assertions.assertArrayEquals(values.toArray(), new String[]{"a1", "b1", "c1", "c2", "c3", "d1", "e1"});
    }

    @Test
    public void testRemoveFirst() {
        LinkedTree<String, String> stream = stream();
        String a1 = stream.filterAndSearch((k, v) -> k.equals("a") ? v : null);
        Assertions.assertEquals(a1, "a1");
        List<String> values = stream.values();
        Assertions.assertArrayEquals(values.toArray(), new String[]{"b1", "c1", "c2", "c3", "d1", "e1"});
    }

    @Test
    public void testRemoveLast() {
        LinkedTree<String, String> stream = stream();
        String e1 = stream.filterAndSearch((k, v) -> k.equals("e") ? v : null);
        Assertions.assertEquals(e1, "e1");
        List<String> values = stream.values();
        Assertions.assertArrayEquals(values.toArray(), new String[]{"a1", "b1", "c1", "c2", "c3", "d1"});
    }

    @Test
    public void testRemove3() {
        LinkedTree<String, String> stream = stream();
        String c1 = stream.filterAndSearch((k, v) -> k.equals("c") ? v : null);
        Assertions.assertEquals(c1, "c1");
        List<String> values = stream.values();
        System.out.println(values);
        Assertions.assertArrayEquals(values.toArray(), new String[]{"a1", "b1", "c2", "c3", "d1", "e1"});
    }

    @Test
    public void testRemove3friends() {
        LinkedTree<String, String> stream = stream();
        String c1 = stream.filterAndSearch((k, v) -> k.equals("c") ? v : null);
        String c2 = stream.filterAndSearch((k, v) -> k.equals("c") ? v : null);
        String c3 = stream.filterAndSearch((k, v) -> k.equals("c") ? v : null);
        Assertions.assertEquals(c1, "c1");
        Assertions.assertEquals(c2, "c2");
        Assertions.assertEquals(c3, "c3");
        List<String> values = stream.values();
        System.out.println(values);
        Assertions.assertArrayEquals(values.toArray(), new String[]{"a1", "b1", "d1", "e1"});
    }

    @Test
    public void testFilterNoSearch() {
        LinkedTree<String, String> stream = stream((k, v) -> k.compareTo("c") < 0);
        String f1 = stream.filterAndSearch((k, v) -> k.equals("f") ? v : null);
        Assertions.assertNull(f1);
        List<String> values = stream.values();
        Assertions.assertArrayEquals(values.toArray(), new String[]{"c1", "d1", "e1"});
    }

    @Test
    public void testFilterSearch() {
        LinkedTree<String, String> stream = stream((k, v) -> k.compareTo("c") < 0);
        String d1 = stream.filterAndSearch((k, v) -> k.equals("d") ? v : null);
        Assertions.assertEquals(d1, "d1");
        List<String> values = stream.values();
        Assertions.assertArrayEquals(values.toArray(), new String[]{"c1", "e1"});
    }

    @Test
    public void testConcurrency() throws Exception {
        LinkedTree<String, String> stream = new LinkedTree<>();
        for (int i = 0; i < 1000; i++) stream.add(String.valueOf(i), String.valueOf(i));
        AtomicInteger count = new AtomicInteger();
        for (int i = 0; i < 25; i++) {
            new Thread(() -> {
                String s = stream.filterAndSearch((k, v) -> k.equals("777") ? v : null);
                if (s != null) count.incrementAndGet();
            }).start();
        }
        Thread.sleep(500);
        Assertions.assertEquals(count.get(), 1);
    }
}
