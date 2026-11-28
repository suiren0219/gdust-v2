plugins { base }

tasks.named("check") {
    dependsOn(":power-core:test", ":chaoxing-core:test")
}
tasks.named("assemble") {
    dependsOn(":power-core:jar", ":chaoxing-core:jar")
}
