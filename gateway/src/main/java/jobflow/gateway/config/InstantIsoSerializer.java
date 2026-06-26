package jobflow.gateway.config;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.ser.std.StdSerializer;

import java.time.Instant;

public class InstantIsoSerializer extends StdSerializer<Instant> {

    public InstantIsoSerializer() {
        super(Instant.class);
    }

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider) {
        gen.writeString(value.toString());
    }
}
