package co.edu.corhuila.csp.catalog.app;

import co.edu.corhuila.csp.catalog.adapter.in.http.ServiceSubjects;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The service subjects allowed to read the availability of a showtime (ADR-042). They come from the environment
 * variable {@code SERVICE_SUBJECTS}, comma separated: absent means {@code booking-service}, and present but
 * empty means nobody, so a wrong value closes the route instead of opening it. Configuration is read here, in
 * the composition root, and nowhere else.
 */
@Configuration
class ServiceSubjectsConfiguration {

    @Bean
    ServiceSubjects serviceSubjects(@Value("${service.subjects:booking-service}") String subjects) {
        return new ServiceSubjects(Arrays.stream(subjects.split(","))
                .map(String::trim)
                .filter(subject -> !subject.isEmpty())
                .collect(Collectors.toSet()));
    }
}
