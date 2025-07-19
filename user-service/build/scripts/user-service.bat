@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  user-service startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and USER_SERVICE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\user-service-plain.jar;%APP_HOME%\lib\spring-boot-devtools-3.5.3.jar;%APP_HOME%\lib\mcsv-common-lib-plain.jar;%APP_HOME%\lib\spring-boot-starter-amqp-3.5.3.jar;%APP_HOME%\lib\spring-cloud-starter-vault-config-4.3.0.jar;%APP_HOME%\lib\spring-boot-starter-data-redis-reactive-3.5.3.jar;%APP_HOME%\lib\spring-boot-starter-security-3.5.3.jar;%APP_HOME%\lib\spring-cloud-starter-netflix-eureka-client-4.2.1.jar;%APP_HOME%\lib\spring-boot-starter-data-r2dbc-3.5.3.jar;%APP_HOME%\lib\spring-boot-starter-oauth2-resource-server-3.5.3.jar;%APP_HOME%\lib\spring-security-oauth2-jose-6.5.1.jar;%APP_HOME%\lib\spring-boot-starter-webflux-3.5.3.jar;%APP_HOME%\lib\keycloak-admin-client-26.0.5.jar;%APP_HOME%\lib\spring-boot-starter-validation-3.5.3.jar;%APP_HOME%\lib\jjwt-impl-0.11.5.jar;%APP_HOME%\lib\jjwt-jackson-0.11.5.jar;%APP_HOME%\lib\jjwt-api-0.11.5.jar;%APP_HOME%\lib\r2dbc-h2-1.0.0.RELEASE.jar;%APP_HOME%\lib\h2-2.3.232.jar;%APP_HOME%\lib\mysql-connector-j-9.2.0.jar;%APP_HOME%\lib\r2dbc-mysql-1.4.1.jar;%APP_HOME%\lib\spring-cloud-vault-config-4.3.0.jar;%APP_HOME%\lib\spring-cloud-starter-loadbalancer-4.2.1.jar;%APP_HOME%\lib\spring-cloud-starter-4.3.0.jar;%APP_HOME%\lib\spring-boot-starter-json-3.5.3.jar;%APP_HOME%\lib\spring-boot-starter-cache-3.5.3.jar;%APP_HOME%\lib\spring-boot-starter-3.5.3.jar;%APP_HOME%\lib\spring-boot-autoconfigure-3.5.3.jar;%APP_HOME%\lib\spring-boot-3.5.3.jar;%APP_HOME%\lib\spring-rabbit-3.2.5.jar;%APP_HOME%\lib\spring-messaging-6.2.8.jar;%APP_HOME%\lib\spring-cloud-netflix-eureka-client-4.2.1.jar;%APP_HOME%\lib\httpclient5-5.5.jar;%APP_HOME%\lib\httpcore5-h2-5.3.4.jar;%APP_HOME%\lib\httpcore5-5.3.4.jar;%APP_HOME%\lib\spring-vault-core-3.2.0.jar;%APP_HOME%\lib\eureka-client-2.0.4.jar;%APP_HOME%\lib\keycloak-client-common-synced-26.0.5.jar;%APP_HOME%\lib\jackson-datatype-jdk8-2.19.1.jar;%APP_HOME%\lib\jackson-datatype-jsr310-2.19.1.jar;%APP_HOME%\lib\resteasy-jackson2-provider-6.2.9.Final.jar;%APP_HOME%\lib\netflix-eventbus-0.3.0.jar;%APP_HOME%\lib\archaius-core-0.7.6.jar;%APP_HOME%\lib\jackson-module-parameter-names-2.19.1.jar;%APP_HOME%\lib\jackson-jakarta-rs-json-provider-2.19.1.jar;%APP_HOME%\lib\jackson-jakarta-rs-base-2.19.1.jar;%APP_HOME%\lib\jackson-module-jakarta-xmlbind-annotations-2.19.1.jar;%APP_HOME%\lib\json-patch-1.13.jar;%APP_HOME%\lib\jackson-databind-2.19.1.jar;%APP_HOME%\lib\jackson-core-2.19.1.jar;%APP_HOME%\lib\jackson-annotations-2.19.1.jar;%APP_HOME%\lib\jackson-dataformat-yaml-2.19.1.jar;%APP_HOME%\lib\lettuce-core-6.6.0.RELEASE.jar;%APP_HOME%\lib\spring-data-r2dbc-3.5.1.jar;%APP_HOME%\lib\r2dbc-pool-1.0.2.RELEASE.jar;%APP_HOME%\lib\spring-webflux-6.2.8.jar;%APP_HOME%\lib\spring-boot-starter-reactor-netty-3.5.3.jar;%APP_HOME%\lib\reactor-netty-http-1.2.7.jar;%APP_HOME%\lib\reactor-netty-core-1.2.7.jar;%APP_HOME%\lib\spring-cloud-loadbalancer-4.2.1.jar;%APP_HOME%\lib\spring-r2dbc-6.2.8.jar;%APP_HOME%\lib\reactor-pool-1.1.3.jar;%APP_HOME%\lib\reactor-extra-3.5.2.jar;%APP_HOME%\lib\reactor-core-3.7.7.jar;%APP_HOME%\lib\spring-data-redis-3.5.1.jar;%APP_HOME%\lib\spring-security-config-6.5.1.jar;%APP_HOME%\lib\spring-security-oauth2-resource-server-6.5.1.jar;%APP_HOME%\lib\spring-security-web-6.5.1.jar;%APP_HOME%\lib\spring-security-oauth2-core-6.5.1.jar;%APP_HOME%\lib\spring-security-core-6.5.1.jar;%APP_HOME%\lib\spring-data-keyvalue-3.5.1.jar;%APP_HOME%\lib\spring-context-support-6.2.8.jar;%APP_HOME%\lib\spring-data-relational-3.5.1.jar;%APP_HOME%\lib\spring-context-6.2.8.jar;%APP_HOME%\lib\spring-aop-6.2.8.jar;%APP_HOME%\lib\r2dbc-spi-1.0.0.RELEASE.jar;%APP_HOME%\lib\spring-web-6.2.8.jar;%APP_HOME%\lib\spring-jdbc-6.2.8.jar;%APP_HOME%\lib\spring-tx-6.2.8.jar;%APP_HOME%\lib\spring-oxm-6.2.8.jar;%APP_HOME%\lib\spring-data-commons-3.5.1.jar;%APP_HOME%\lib\spring-beans-6.2.8.jar;%APP_HOME%\lib\spring-amqp-3.2.5.jar;%APP_HOME%\lib\spring-expression-6.2.8.jar;%APP_HOME%\lib\spring-core-6.2.8.jar;%APP_HOME%\lib\nimbus-jose-jwt-9.37.3.jar;%APP_HOME%\lib\microprofile-openapi-api-3.1.1.jar;%APP_HOME%\lib\resteasy-client-6.2.9.Final.jar;%APP_HOME%\lib\resteasy-multipart-provider-6.2.9.Final.jar;%APP_HOME%\lib\resteasy-jaxb-provider-6.2.9.Final.jar;%APP_HOME%\lib\tomcat-embed-el-10.1.42.jar;%APP_HOME%\lib\hibernate-validator-8.0.2.Final.jar;%APP_HOME%\lib\spring-boot-starter-logging-3.5.3.jar;%APP_HOME%\lib\resteasy-client-api-6.2.9.Final.jar;%APP_HOME%\lib\resteasy-core-6.2.9.Final.jar;%APP_HOME%\lib\resteasy-core-spi-6.2.9.Final.jar;%APP_HOME%\lib\jakarta.annotation-api-2.1.1.jar;%APP_HOME%\lib\snakeyaml-2.4.jar;%APP_HOME%\lib\amqp-client-5.25.0.jar;%APP_HOME%\lib\micrometer-observation-1.15.1.jar;%APP_HOME%\lib\redis-authx-core-0.1.1-beta2.jar;%APP_HOME%\lib\spectator-api-1.7.3.jar;%APP_HOME%\lib\logback-classic-1.5.18.jar;%APP_HOME%\lib\log4j-to-slf4j-2.24.3.jar;%APP_HOME%\lib\jul-to-slf4j-2.0.17.jar;%APP_HOME%\lib\netflix-infix-0.3.0.jar;%APP_HOME%\lib\servo-core-0.5.3.jar;%APP_HOME%\lib\slf4j-api-2.0.17.jar;%APP_HOME%\lib\spring-cloud-context-4.3.0.jar;%APP_HOME%\lib\spring-cloud-commons-4.3.0.jar;%APP_HOME%\lib\bcprov-jdk18on-1.80.jar;%APP_HOME%\lib\netty-resolver-dns-native-macos-4.1.122.Final-osx-x86_64.jar;%APP_HOME%\lib\netty-resolver-dns-classes-macos-4.1.122.Final.jar;%APP_HOME%\lib\netty-resolver-dns-4.1.122.Final.jar;%APP_HOME%\lib\netty-handler-proxy-4.1.122.Final.jar;%APP_HOME%\lib\netty-codec-http2-4.1.122.Final.jar;%APP_HOME%\lib\netty-codec-http-4.1.122.Final.jar;%APP_HOME%\lib\netty-handler-4.1.122.Final.jar;%APP_HOME%\lib\netty-transport-native-epoll-4.1.122.Final-linux-x86_64.jar;%APP_HOME%\lib\netty-transport-classes-epoll-4.1.122.Final.jar;%APP_HOME%\lib\netty-transport-native-unix-common-4.1.122.Final.jar;%APP_HOME%\lib\netty-codec-socks-4.1.122.Final.jar;%APP_HOME%\lib\netty-codec-dns-4.1.122.Final.jar;%APP_HOME%\lib\netty-codec-4.1.122.Final.jar;%APP_HOME%\lib\netty-transport-4.1.122.Final.jar;%APP_HOME%\lib\netty-resolver-4.1.122.Final.jar;%APP_HOME%\lib\netty-buffer-4.1.122.Final.jar;%APP_HOME%\lib\netty-common-4.1.122.Final.jar;%APP_HOME%\lib\reactive-streams-1.0.4.jar;%APP_HOME%\lib\javax.annotation-api-1.2.jar;%APP_HOME%\lib\xstream-1.4.20.jar;%APP_HOME%\lib\jakarta.ws.rs-api-3.1.0.jar;%APP_HOME%\lib\jakarta.inject-api-2.0.1.jar;%APP_HOME%\lib\httpclient-4.5.14.jar;%APP_HOME%\lib\jsr305-3.0.2.jar;%APP_HOME%\lib\commons-configuration-1.10.jar;%APP_HOME%\lib\compactmap-2.0.jar;%APP_HOME%\lib\jettison-1.5.4.jar;%APP_HOME%\lib\evictor-1.0.0.jar;%APP_HOME%\lib\spring-security-crypto-6.5.1.jar;%APP_HOME%\lib\spring-jcl-6.2.8.jar;%APP_HOME%\lib\jcip-annotations-1.0-1.jar;%APP_HOME%\lib\commons-logging-jboss-logging-1.0.0.Final.jar;%APP_HOME%\lib\jboss-logging-3.6.1.Final.jar;%APP_HOME%\lib\commons-codec-1.18.0.jar;%APP_HOME%\lib\jakarta.mail-api-2.1.3.jar;%APP_HOME%\lib\angus-mail-2.0.3.jar;%APP_HOME%\lib\apache-mime4j-storage-0.8.11.jar;%APP_HOME%\lib\apache-mime4j-dom-0.8.11.jar;%APP_HOME%\lib\apache-mime4j-core-0.8.11.jar;%APP_HOME%\lib\commons-io-2.11.0.jar;%APP_HOME%\lib\jakarta.xml.bind-api-4.0.2.jar;%APP_HOME%\lib\codemodel-4.0.5.jar;%APP_HOME%\lib\jaxb-core-4.0.5.jar;%APP_HOME%\lib\jaxb-jxc-4.0.5.jar;%APP_HOME%\lib\jaxb-runtime-4.0.5.jar;%APP_HOME%\lib\txw2-4.0.5.jar;%APP_HOME%\lib\jaxb-xjc-4.0.5.jar;%APP_HOME%\lib\xsom-4.0.5.jar;%APP_HOME%\lib\istack-commons-runtime-4.1.2.jar;%APP_HOME%\lib\istack-commons-tools-4.1.2.jar;%APP_HOME%\lib\relaxng-datatype-4.0.3.jar;%APP_HOME%\lib\rngom-4.0.3.jar;%APP_HOME%\lib\jakarta.validation-api-3.0.2.jar;%APP_HOME%\lib\classmate-1.7.0.jar;%APP_HOME%\lib\spring-retry-2.0.12.jar;%APP_HOME%\lib\micrometer-commons-1.15.1.jar;%APP_HOME%\lib\commons-math-2.2.jar;%APP_HOME%\lib\mxparser-1.2.2.jar;%APP_HOME%\lib\guava-16.0.jar;%APP_HOME%\lib\commons-lang-2.6.jar;%APP_HOME%\lib\commons-logging-1.2.jar;%APP_HOME%\lib\dexx-collections-0.2.jar;%APP_HOME%\lib\httpcore-4.4.16.jar;%APP_HOME%\lib\jandex-2.4.5.Final.jar;%APP_HOME%\lib\jakarta.activation-api-2.1.3.jar;%APP_HOME%\lib\angus-activation-2.0.2.jar;%APP_HOME%\lib\asyncutil-0.1.0.jar;%APP_HOME%\lib\logback-core-1.5.18.jar;%APP_HOME%\lib\log4j-api-2.24.3.jar;%APP_HOME%\lib\commons-jxpath-1.3.jar;%APP_HOME%\lib\joda-time-2.3.jar;%APP_HOME%\lib\servlet-api-2.5.jar;%APP_HOME%\lib\antlr-runtime-3.4.jar;%APP_HOME%\lib\gson-2.13.1.jar;%APP_HOME%\lib\annotations-2.0.0.jar;%APP_HOME%\lib\xmlpull-1.1.3.1.jar;%APP_HOME%\lib\stringtemplate-3.2.1.jar;%APP_HOME%\lib\antlr-2.7.7.jar;%APP_HOME%\lib\error_prone_annotations-2.38.0.jar


@rem Execute user-service
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %USER_SERVICE_OPTS%  -classpath "%CLASSPATH%"  %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable USER_SERVICE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%USER_SERVICE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
