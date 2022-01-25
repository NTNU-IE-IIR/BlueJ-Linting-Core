# BlueJ-Linting-Core
![release](https://img.shields.io/github/v/release/NTNU-IE-IIR/BlueJ-Linting-Core)
![license](https://img.shields.io/github/license/NTNU-IE-IIR/BlueJ-Linting-Core)

A core library designed to be used for writing linting extensions for the BlueJ IDE.


This library provides core functionality used by the [Checkstyle][1] and [SonarLint][2] extensions for BlueJ.
It covers datatypes for violations and rule definitions, a ViolationManager that can be used to track these violations. 


A default PackageEventHandler and FilesChangeHandler implementation is provided, for handling when projects open/close and when files are changed/removed. These implementations are optional.


## Using this library
Because BlueJ artifacts are not uploaded to Maven Central, we have decided to not publish this library to Maven Central. 
You can however, utilize [JitPack][3] to install this library. 
Instructions on how to use this library with your choice of build system can be found [here][4]. 


A full example can be found in the [Checkstyle][1] extension repository.

Javadoc for this library can be found [here][5].

## Issues
Are you experiencing bugs/problems using this library? 

Submit a [bug report](https://github.com/NTNU-IE-IIR/BlueJ-Linting-Core/issues/new?assignees=&labels=&template=bug_report.md&title=) with detailed reproduction steps.


## Contributing
Contributions are welcome. The aim of this library is simplicity and usability, please discuss the changes with us in a [feature request](https://github.com/NTNU-IE-IIR/BlueJ-Linting-Core/issues/new?assignees=&labels=&template=feature_request.md&title=) before submitting a Pull Request.


[1]: https://github.com/NTNU-IE-IIR/BlueJ-Checkstyle-Plugin
[2]: https://github.com/NTNU-IE-IIR/BlueJ-SonarLint-Plugin
[3]: https://jitpack.io/
[4]: https://jitpack.io/#NTNU-IE-IIR/BlueJ-Linting-Core 
[5]: https://NTNU-IE-IIR.github.io/BlueJ-Linting-Core