package ch.vilki.jfxldap.gui

data class MessageBox(val t:String, val h:String, val d:String?)

class Messages{
    companion object{
        val MB1= MessageBox("Enter master password","Not secured with windows ",null)
        val MB2=
            MessageBox("Config file locked","Config file is windows secured with other user","You are using config file in home directory, " +
                " which is windows secured with other user. To unlock the config file, you must enter the master password of the file." +
                "If you do not know the password, you shall not be able to read any passwords from the file")
        val MB3= MessageBox("Enter master password","Not secured with current user",null)
        val MB4=
            MessageBox("Config file error","Did not find config file, create new config with Master-Password?","If you choose not to set master password, you will not be able to store connection passwords in " +
                "config file. If you are running windows, you will not be asked for master password again, after you set one, but " +
                "do not forget the password, as you may need it if you want to migrate config file to other pc!")
        val MB5= MessageBox("No password entered",
            "You have not entered any password, create config file without password?",
            "If you choose not to set master password, you will not be able to store connection passwords in " +
                    "config file. If you are running windows, you will not be asked for master password again, after you set one, but" +
                    "do not forget the password, as you may need it if you want to migrate config file to other pc!")

    }

}


