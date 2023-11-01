package io.aston;

import io.aston.worker.LinkedStream;

public class XStream {
    public static void main(String[] args) {

        LinkedStream<String, String> stream = new LinkedStream<>() {
            @Override
            public boolean cleanFilterFn(String key, String value) {
                return key.compareTo("c") < 0;
            }
        };
        stream.add("a", "a");
        stream.add("b", "b");
        stream.add("c", "c");
        stream.add("d", "d");
        stream.add("e", "e");


        System.out.println(stream.filterAndSearch((k, v) -> k.equals("c") ? v : null));
        System.out.println(stream.filterAndSearch((k, v) -> k.equals("a") ? v : null));
        System.out.println(stream.values());

        System.out.println(stream.filterAndSearch((k, v) -> k.equals("e") ? v : null));
        System.out.println(stream.filterAndSearch((k, v) -> k.equals("b") ? v : null));
        System.out.println(stream.filterAndSearch((k, v) -> k.equals("d") ? v : null));
        System.out.println(stream.filterAndSearch((k, v) -> k.equals("x") ? v : null));

    }
}
