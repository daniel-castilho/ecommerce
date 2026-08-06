package com.loja.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the admin RBAC contract against drift between the two enforcement layers:
 *
 * <ol>
 *   <li><b>Pages</b> — every admin {@code .xhtml} (under {@code admin-dashboard/},
 *       {@code user-account/admin/}, and {@code product-catalog/manageProduct.xhtml})
 *       must be covered by a protected {@code <url-pattern>} in {@code web.xml}, and
 *       vice versa (no dead constraint protecting nothing).</li>
 *   <li><b>Beans</b> — every admin JSF bean (the {@code *Bean} classes backing admin
 *       pages: all of {@code admin-dashboard/.../adapter/in/web}, the
 *       {@code Admin*} beans in {@code user-account}, and
 *       {@code ManageProductBean} in {@code product-catalog}) must be decorated with
 *       both {@code @Named} and {@code @RolesAllowed("ADMIN")}.</li>
 * </ol>
 *
 * <p>Written while closing the admin-dashboard epic (S25/S26): page guards lived only
 * in {@code web.xml} and bean guards only as {@code @RolesAllowed} annotations, with no
 * test tying the two together. A bean added without the annotation, or a page added
 * outside a protected pattern, would open an admin surface to any logged-in user.
 *
 * <p>Runs on source files, like {@link StatusBadgeCssCoverageTest}; the 'web' module
 * must be the working directory (Maven Surefire's default).
 */
class AdminAccessControlCoverageTest {

    private static final Path WEBAPP = Path.of("src/main/webapp");

    private static final Set<Path> ADMIN_PAGE_ROOTS = Set.of(
            Path.of("admin-dashboard"),
            Path.of("user-account/admin")
    );

    private static final Path MANAGE_PRODUCT_PAGE = Path.of("product-catalog/manageProduct.xhtml");

    private static final List<Path> ADMIN_BEAN_SOURCES = List.of(
            Path.of("../admin-dashboard/src/main/java/com/loja/admindashboard/adapter/in/web"),
            Path.of("../user-account/src/main/java/com/loja/useraccount/adapter/in/web/AdminUsersBean.java"),
            Path.of("../product-catalog/src/main/java/com/loja/productcatalog/adapter/in/web/ManageProductBean.java"),
            Path.of("../product-reviews/src/main/java/com/loja/productreviews/adapter/in/web/ReviewModerationBean.java"),
            Path.of("../product-reviews/src/main/java/com/loja/productreviews/adapter/in/web/ReviewDetailBean.java")
    );

    private static final Pattern SECURITY_CONSTRAINT_PATTERN =
            Pattern.compile("<security-constraint>(.*?)</security-constraint>", Pattern.DOTALL);
    private static final Pattern URL_PATTERN_PATTERN =
            Pattern.compile("<url-pattern>([^<]+)</url-pattern>");
    private static final Pattern ADMIN_AUTH_CONSTRAINT_PATTERN =
            Pattern.compile("<auth-constraint>.*?<role-name>ADMIN</role-name>.*?</auth-constraint>", Pattern.DOTALL);

    @Test
    void everyAdminPageIsCoveredByAProtectedUrlPatternInWebXml() {
        String webXml = readFile(WEBAPP.resolve("WEB-INF/web.xml"));
        List<String> protectedPatterns = protectedUrlPatterns(webXml);
        assertThat(protectedPatterns)
                .as("web.xml must declare at least one ADMIN-protected security-constraint url-pattern")
                .isNotEmpty();

        List<String> adminPages = adminPages().stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        assertThat(adminPages)
                .as("expected to find admin pages to protect")
                .isNotEmpty();

        List<String> uncovered = adminPages.stream()
                .filter(url -> protectedPatterns.stream().noneMatch(p -> matches(url, p)))
                .collect(Collectors.toList());
        assertThat(uncovered)
                .as("admin page(s) not covered by any ADMIN url-pattern in web.xml. "
                        + "Add them to a <security-constraint> or they are reachable by any logged-in user.")
                .isEmpty();
    }

    @Test
    void everyAdminProtectedPatternInWebXmlCoversAtLeastOnePage() {
        String webXml = readFile(WEBAPP.resolve("WEB-INF/web.xml"));
        List<String> adminPages = adminPages();

        List<String> deadPatterns = protectedUrlPatterns(webXml).stream()
                .filter(pattern -> adminPages.stream().noneMatch(url -> matches(url, pattern)))
                .collect(Collectors.toList());
        assertThat(deadPatterns)
                .as("url-pattern(s) in web.xml protecting no existing admin page. "
                        + "Dead constraints hide moved/renamed admin surfaces — remove or fix them.")
                .isEmpty();
    }

    @Test
    void webXmlDeclaresAdminRoleInAnAuthConstraint() {
        String webXml = readFile(WEBAPP.resolve("WEB-INF/web.xml"));
        assertThat(ADMIN_AUTH_CONSTRAINT_PATTERN.matcher(webXml).find())
                .as("web.xml must contain <auth-constraint> with <role-name>ADMIN</role-name>")
                .isTrue();
    }

    @Test
    void everyAdminBeanIsNamedAndAnnotatedWithRolesAllowedAdmin() {
        List<String> unprotected = new ArrayList<>();
        List<Path> beanFiles = adminBeanFiles();
        assertThat(beanFiles)
                .as("expected to find admin bean source files to inspect")
                .isNotEmpty();

        for (Path beanFile : beanFiles) {
            String source = readFile(beanFile);
            if (!source.contains("@Named") || !source.contains("@RolesAllowed(\"ADMIN\")")) {
                unprotected.add(WEBAPP.relativize(beanFile).toString());
            }
        }
        assertThat(unprotected)
                .as("admin JSF bean(s) missing @Named or @RolesAllowed(\"ADMIN\"). "
                        + "Without the annotation the container will not restrict the bean to ADMIN callers.")
                .isEmpty();
    }

    private static boolean matches(String url, String pattern) {
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return url.equals(prefix) || url.startsWith(prefix + "/");
        }
        return url.equals(pattern);
    }

    private static List<String> protectedUrlPatterns(String webXml) {
        List<String> patterns = new ArrayList<>();
        Matcher constraint = SECURITY_CONSTRAINT_PATTERN.matcher(webXml);
        while (constraint.find()) {
            Matcher urlPattern = URL_PATTERN_PATTERN.matcher(constraint.group(1));
            while (urlPattern.find()) {
                patterns.add(urlPattern.group(1));
            }
        }
        return patterns;
    }

    private static List<String> adminPages() {
        List<String> urls = new ArrayList<>();
        for (Path root : ADMIN_PAGE_ROOTS) {
            Path dir = WEBAPP.resolve(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.filter(p -> p.toString().endsWith(".xhtml"))
                        .map(p -> "/" + WEBAPP.relativize(p).toString().replace('\\', '/'))
                        .forEach(urls::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        Path manageProduct = WEBAPP.resolve(MANAGE_PRODUCT_PAGE);
        if (Files.isRegularFile(manageProduct)) {
            urls.add("/" + WEBAPP.relativize(manageProduct).toString());
        }
        return urls;
    }

    private static List<Path> adminBeanFiles() {
        List<Path> files = new ArrayList<>();
        Path dir = ADMIN_BEAN_SOURCES.get(0);
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path single : ADMIN_BEAN_SOURCES.subList(1, ADMIN_BEAN_SOURCES.size())) {
            if (Files.isRegularFile(single)) {
                files.add(single);
            } else if (Files.isDirectory(single)) {
                try (Stream<Path> paths = Files.list(single)) {
                    paths.filter(p -> p.toString().endsWith("AdminBean.java"))
                            .forEach(files::add);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        return files;
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Could not read " + path.toAbsolutePath()
                            + " -- this test must run with the 'web' module as the working "
                            + "directory (Maven Surefire's default).",
                    e);
        }
    }
}
