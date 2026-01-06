[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/com.colofabrix.scala/declinio_3.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.colofabrix.scala%22%20AND%20a:%22declinio_3%22)

# Declinio

**Declinio** is a Scala 3 library that provides seamless integration between
[Decline](https://ben.kirw.in/decline/) and [Cats Effect](https://typelevel.org/cats-effect/)
avoiding the awkward constructor syntax of Decline.

It simplifies the creation of command-line applications by combining the declarative argument
parsing of Decline with the powerful effect management of Cats Effect.

## Features

- **Simple Integration** - Combine Decline's argument parsing with Cats Effect's IO monad
- **Minimal Boilerplate** - Just extend a trait and define your options
- **ReaderT Support** - Use ReaderT for dependency injection patterns
- **Configurable** - Support for custom version flags, help messages, and more
- **Type-Safe** - Leverage Scala's type system for compile-time safety
- **Effect-Aware** - Full support for Cats Effect's resource management and error handling
- **Custom Effect Types** - Use any effect type with a natural transformation to IO

## Installation

Add the following dependencies to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "com.colofabrix.scala" %% "declinio"    % "1.0.0",
  "com.monovore"         %% "decline"     % <version>,  // Required peer dependency
  "org.typelevel"        %% "cats-effect" % <version>,  // Required peer dependency
)
```

> **Note:** Declinio uses `Provided` scope for its dependencies, giving you full control over
> the versions of Decline and Cats Effect in your project.

## Quick Start

### Basic Application with Configuration

Create a command-line application that parses arguments using `IODeclineApp`:

```scala
import cats.effect.*
import com.colofabrix.scala.declinio.*
import com.monovore.decline.*

// Define your configuration case class
case class Config(name: String, count: Int, verbose: Boolean)

object MyApp extends IODeclineApp[Config]:

  override def name: String =
    "my-app"

  override def header: String =
    "A simple example application"

  override def version: String =
    "1.0.0"

  override def options: Opts[Config] =
    val nameOpt    = Opts.option[String]("name", help = "Your name")
    val countOpt   = Opts.option[Int]("count", help = "Number of greetings").withDefault(1)
    val verboseOpt = Opts.flag("verbose", help = "Verbose output").orFalse
    (nameOpt, countOpt, verboseOpt).mapN(Config.apply)

  override def runWithConfig(config: Config): IO[ExitCode] =
    for
      _ <- IO.println(s"Hello, ${config.name}!")
      _ <- if (config.verbose) IO.println(s"Greeting you ${config.count} times...") else IO.unit
      _ <- (1 until config.count).toList.traverse_(_ => IO.println(s"Hello ${config.name}!"))
    yield ExitCode.Success
```

Run with:
```bash
sbt "run --name World --count 3 --verbose"
```

### Simple Application without Configuration

For applications that don't need command-line arguments, use `IOUnitDeclineApp`:

```scala
import cats.effect.*
import com.colofabrix.scala.declinio.*

object SimpleApp extends IOUnitDeclineApp:

  override def name: String =
    "simple-app"

  override def header: String =
    "A simple application without arguments"

  override def version: String =
    "1.0.0"

  override def runNoConfig: IO[ExitCode] =
    IO.println("Hello, World!").as(ExitCode.Success)
```

### Application with ReaderT

For applications that prefer using `ReaderT` for dependency injection patterns, use
`IODeclineReaderApp`:

```scala
import cats.effect.*
import cats.data.ReaderT
import com.colofabrix.scala.declinio.*
import com.monovore.decline.*

case class AppConfig(apiUrl: String, timeout: Int)

object ReaderApp extends IODeclineReaderApp[AppConfig]:

  override def name: String =
    "reader-app"

  override def header: String =
    "Application using ReaderT"

  override def version: String =
    "1.0.0"

  override def options: Opts[AppConfig] =
    val apiUrl  = Opts.option[String]("api-url", help = "API endpoint URL")
    val timeout = Opts.option[Int]("timeout", help = "Request timeout in seconds").withDefault(30)
    (apiUrl, timeout).mapN(AppConfig.apply)

  override def runWithReader: ReaderT[IO, AppConfig, ExitCode] =
    ReaderT { config =>
      for
        _ <- IO.println(s"Connecting to ${config.apiUrl}")
        _ <- IO.println(s"Timeout: ${config.timeout}s")
      yield ExitCode.Success
    }
```

### Custom Effect Type

For applications using a custom effect type other than `IO`, extend `DeclineApp` or
`DeclineReaderApp` directly and provide a natural transformation from your effect to `IO`:

```scala
import cats.~>
import cats.effect.*
import cats.data.ReaderT
import com.colofabrix.scala.declinio.*
import com.monovore.decline.*

// Example: Using a custom effect type with environment
type AppIO[A] = ReaderT[IO, AppEnv, A]

case class AppEnv(logger: String => IO[Unit])
case class Config(debug: Boolean)

object CustomEffectApp extends DeclineReaderApp[AppIO, Config]:

  override def name: String =
    "custom-app"

  override def header: String =
    "Application with custom effect"

  override def version: String =
    "1.0.0"

  override def options: Opts[Config] =
    Opts
      .flag("debug", "Enable debug mode", short = "d")
      .orFalse
      .map(Config.apply)

  // Provide the natural transformation from AppIO to IO
  override protected def runEffectToIO: AppIO ~> IO =
    new (AppIO ~> IO):
      def apply[A](fa: AppIO[A]): IO[A] =
        fa.run(AppEnv(msg => IO.println(s"[LOG] $msg")))

  override def runWithReader: ReaderT[AppIO, Config, ExitCode] =
    ReaderT { config =>
      ReaderT { env =>
        for
          _ <- env.logger(s"Debug mode: ${config.debug}")
          _ <- IO.println("Application running")
        yield ExitCode.Success
      }
    }
```

## Trait Hierarchy

Declinio provides a hierarchy of traits to suit different use cases:

```
DeclineReaderApp[F[_], A]                 (base trait, uses ReaderT)
    ├── DeclineApp[F[_], A]               (uses runWithConfig method)
    │       └── IODeclineApp[A]           (IO-specific)
    │               └── IOUnitDeclineApp  (no configuration)
    └── IODeclineReaderApp[A]             (IO-specific, uses ReaderT)
```

## API Reference

### DeclineReaderApp[F, A]

The base trait that provides Decline integration for any effect type `F[_]`. Uses `ReaderT` to pass
the parsed configuration to the application.

| Member           | Type                          | Description                                          |
|------------------|-------------------------------|------------------------------------------------------|
| `name`           | `String`                      | Name of the application (required)                   |
| `header`         | `String`                      | Short description of the application (required)      |
| `version`        | `String`                      | Version of the application (optional, default: "")   |
| `options`        | `Opts[A]`                     | Decline command-line options (required)              |
| `helpFlag`       | `Boolean`                     | Display help on wrong arguments (default: true)      |
| `runWithReader`  | `ReaderT[F, A, ExitCode]`     | Main application logic using ReaderT (required)      |
| `runEffectToIO`  | `F ~> IO`                     | Natural transformation from F to IO (required)       |

### DeclineApp[F, A]

Extends `DeclineReaderApp` and provides a simpler interface using `runWithConfig` instead of `ReaderT`.

| Member           | Type               | Description                               |
|------------------|--------------------|-------------------------------------------|
| `runWithConfig`  | `A => F[ExitCode]` | Main application logic (required)         |

### IODeclineReaderApp[A]

A convenience trait that extends `DeclineReaderApp[IO, A]` with a pre-defined identity
transformation for IO. Use this when you want to use `ReaderT` with `IO`.

### IODeclineApp[A]

A convenience trait that extends `DeclineApp[IO, A]`. Use this for standard Cats Effect IO
applications with the simple `runWithConfig` interface.

### IOUnitDeclineApp

A convenience trait for applications that don't need command-line arguments. Extends
`IODeclineApp[Unit]`.

| Member        | Type           | Description                        |
|---------------|----------------|------------------------------------|
| `runNoConfig` | `IO[ExitCode]` | Main application logic (required)  |

## Advanced Usage

### Environment Variables

Declinio supports environment variables through Decline's native functionality:

```scala
override def options: Opts[Config] =
  val apiKey = Opts.env[String]("API_KEY", help = "API key for authentication")
  val debug  = Opts.flag("debug", "Enable debug mode").orFalse
  (apiKey, debug).mapN(Config.apply)
```

### Subcommands

Use Decline's subcommand support:

```scala
sealed trait Command
case class Add(item: String) extends Command
case class Remove(id: Int) extends Command

override def options: Opts[Command] =
  val add =
    Opts.subcommand("add", "Add a new item") {
      Opts.argument[String]("item").map(Add.apply)
    }

  val remove =
    Opts.subcommand("remove", "Remove an item") {
      Opts.argument[Int]("id").map(Remove.apply)
    }

  add orElse remove
```

### Error Handling

Leverage Cats Effect's error handling:

```scala
override def runWithConfig(config: Config): IO[ExitCode] =
  myProgram(config).handleErrorWith { error =>
    IO.errorln(s"Error: ${error.getMessage}").as(ExitCode.Error)
  }
```

### Resource Management

Use Cats Effect's resource management:

```scala
override def runWithConfig(config: Config): IO[ExitCode] =
  Resource
    .make(IO.println("Acquiring resource...") *> IO.pure("resource"))(
      _ => IO.println("Releasing resource...")
    )
    .use { resource =>
      IO.println(s"Using $resource").as(ExitCode.Success)
    }
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

Declinio is released under the MIT license. See [LICENSE](LICENSE) for details.

## Author

[Fabrizio Colonna](mailto:colofabrix@tin.it)

## See Also

- [Decline](https://ben.kirw.in/decline/) - Composable command-line parsing for Scala
- [Cats Effect](https://typelevel.org/cats-effect/) - The pure asynchronous runtime for Scala
