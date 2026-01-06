@echo off
setlocal

:: Nom du fichier source


:: Nom du JAR à générer
set JAR_NAME=servlet.jar

echo 🔧 Compilation du fichier Java...
javac -cp "C:\xampp\tomcat\lib\servlet-api.jar" -d . *.java

echo 📦 Création du JAR : %JAR_NAME%
jar cf %JAR_NAME% servlet

echo ✅ JAR généré : %JAR_NAME%
endlocal
pause