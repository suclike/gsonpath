package adapter.auto.polymorphism.failures;

import adapter.auto.polymorphism.Type1;
import gsonpath.AutoGsonAdapter;
import gsonpath.GsonSubtype;

@AutoGsonAdapter
class TypesList_MultipleKeys {
    @GsonSubtype(
            subTypeKey = "type",
            stringValueSubtypes = {
                    @GsonSubtype.StringValueSubtype(value = "type1", subtype = Type1.class)
            },
            integerValueSubtypes = {
                    @GsonSubtype.IntegerValueSubtype(value = 1, subtype = Type1.class)
            }
    )
    Type[] items;
}
