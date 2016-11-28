package adapter.loader.source2;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import gsonpath.internal.TypeAdapterLoader;
import java.lang.Override;

public final class PackagePrivateTypeAdapterLoader implements TypeAdapterLoader {
    @Override
    public TypeAdapter create(Gson gson, TypeToken type) {
        Class rawType = type.getRawType();
        if (rawType.equals(TestLoaderSource.class)) {
            return new TestLoaderSource_GsonTypeAdapter(gson);

        } else if (rawType.equals(TestLoaderSource2.class)) {
            return new TestLoaderSource2_GsonTypeAdapter(gson);
        }

        return null;
    }
}