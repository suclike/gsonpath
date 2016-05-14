package adapter.auto.naming_policy.lowercase_underscores;

import com.google.gson.FieldNamingPolicy;

import gsonpath.AutoGsonAdapter;

@AutoGsonAdapter(fieldNamingPolicy = FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
public class TestNamePolicyLowerCaseUnderscores {
    public int testValue;
}