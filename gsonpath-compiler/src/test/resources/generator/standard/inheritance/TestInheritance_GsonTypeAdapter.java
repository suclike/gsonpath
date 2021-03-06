package generator.standard.inheritance;

import static gsonpath.GsonUtil.*;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.Override;

public final class TestInheritance_GsonTypeAdapter extends TypeAdapter<TestInheritance> {
    private final Gson mGson;

    public TestInheritance_GsonTypeAdapter(Gson gson) {
        this.mGson = gson;
    }

    @Override
    public TestInheritance read(JsonReader in) throws IOException {

        // Ensure the object is not null.
        if (!isValidValue(in)) {
            return null;
        }
        TestInheritance result = new TestInheritance();

        int jsonFieldCounter0 = 0;
        in.beginObject();

        while (in.hasNext()) {
            if (jsonFieldCounter0 == 1) {
                in.skipValue();
                continue;
            }

            switch (in.nextName()) {
                case "Json1":
                    jsonFieldCounter0++;

                    Integer value_Json1 = getIntegerSafely(in);
                    if (value_Json1 != null) {
                        result.value1 = value_Json1;
                    }
                    break;

                default:
                    in.skipValue();
                    break;
            }
        }


        in.endObject();
        return result;
    }

    @Override
    public void write(JsonWriter out, TestInheritance value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        // Begin
        out.beginObject();
        int obj0 = value.value1;
        out.name("Json1");
        out.value(obj0);

        // End
        out.endObject();
    }
}