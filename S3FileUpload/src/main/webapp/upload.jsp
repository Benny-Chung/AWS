<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="ISO-8859-1">
<title>Upload image</title>
</head>
<body>
<div><h1>Upload image:</h1></div>
 
<div>
    <form action="upload" method="post" enctype="multipart/form-data">
        <p><input type="file" name="file" required /></p>
        <p><button type="submit">Submit</button></p>
    </form>
</div>
</body>
</html>