package com.backend.backend.service.matching;

import java.util.List;

/** Known technology names used by the non-AI fallbacks to spot skills in free text. */
public final class SkillDictionary {

    public static final List<String> TECHNOLOGIES = List.of(
            "Java", "Spring Boot", "Spring", "Hibernate", "Kotlin", "Scala", "Python", "Django", "Flask", "FastAPI",
            "JavaScript", "TypeScript", "Node.js", "NestJS", "Angular", "React", "React Native", "Vue",
            "Svelte", "Next.js", "HTML", "CSS", "Sass", "Tailwind", "Flutter", "Dart", "Swift", "Android", "iOS",
            "C++", "C#", ".NET", "ASP.NET", "Golang", "Rust", "PHP", "Laravel", "Symfony", "Ruby", "Rails",
            "SQL", "PostgreSQL", "MySQL", "MariaDB", "Oracle", "SQL Server", "MongoDB", "Redis", "Elasticsearch",
            "Cassandra", "Kafka", "RabbitMQ", "Spark", "Hadoop", "Airflow", "dbt", "Snowflake", "Databricks",
            "Power BI", "Tableau", "Excel", "Pandas", "NumPy", "TensorFlow", "PyTorch", "scikit-learn",
            "Docker", "Kubernetes", "Terraform", "Ansible", "Jenkins", "GitLab CI", "GitHub Actions", "CI/CD",
            "AWS", "Azure", "GCP", "Linux", "Bash", "Git", "REST APIs", "GraphQL", "gRPC", "Microservices",
            "JUnit", "Selenium", "Cypress", "Jest", "Agile", "Scrum", "Jira", "SAP", "Salesforce", "Figma");

    private SkillDictionary() {
    }

    public static List<String> findIn(String text) {
        String lower = text == null ? "" : text.toLowerCase();
        return TECHNOLOGIES.stream().filter(skill -> TextMatcher.mentions(lower, skill)).toList();
    }
}
