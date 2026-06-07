package jobflow.domain.analytics;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import jobflow.domain.skill.ExperienceTagCode;

final class ExperienceTagCodeTestFactory {

    private ExperienceTagCodeTestFactory() {
    }

    static ExperienceTagCode create(
            String code,
            String name,
            String description
    ) {
        try {
            Constructor<ExperienceTagCode> constructor = ExperienceTagCode.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ExperienceTagCode tagCode = constructor.newInstance();
            setField(tagCode, "code", code);
            setField(tagCode, "name", name);
            setField(tagCode, "description", description);
            setField(tagCode, "createdAt", LocalDateTime.now());
            return tagCode;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create ExperienceTagCode for test", exception);
        }
    }

    private static void setField(
            Object target,
            String fieldName,
            Object value
    ) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
