package ocaiosantos.com.github.cleanarchutils

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBUI
import java.io.IOException
import java.io.Serializable
import javax.swing.BoxLayout
import javax.swing.JPanel

class SelectFoldersDialog(private val options: Array<Serializable>) : DialogWrapper(true) {
    private val checkBoxes = mutableListOf<JBCheckBox>()
    private val subCheckboxes = mutableMapOf<JBCheckBox, List<JBCheckBox>>()
    private val nestedSubCheckboxes = mutableMapOf<JBCheckBox, List<JBCheckBox>>()

    init {
        init()
        title = "Customize sua estrutura"
    }

    override fun createCenterPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10)
        }

        options.forEach { option ->
            when (option) {
                is String -> {
                    val checkBox = JBCheckBox(option, false)
                    checkBoxes.add(checkBox)
                    panel.add(checkBox)
                }

                is Pair<*, *> -> {
                    val parentCheckBox = JBCheckBox(option.first.toString(), true)
                    checkBoxes.add(parentCheckBox)
                    panel.add(parentCheckBox)

                    val subPanel = JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        border = JBUI.Borders.emptyLeft(20)
                    }

                    val subCheckBoxesList = mutableListOf<JBCheckBox>()

                    (option.second as? Array<*>)?.forEach { subOption ->
                        when (subOption) {
                            is String -> {
                                val subCheckBox = JBCheckBox(subOption, true)
                                subCheckBoxesList.add(subCheckBox)
                                subPanel.add(subCheckBox)
                            }

                            is Pair<*, *> -> {
                                val nestedCheckBox = JBCheckBox(subOption.first.toString(), true)
                                subCheckBoxesList.add(nestedCheckBox)

                                val nestedPanel = JPanel().apply {
                                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                                    border = JBUI.Borders.emptyLeft(20)
                                }

                                val nestedSubCheckBoxesList = mutableListOf<JBCheckBox>()
                                (subOption.second as? Array<*>)?.forEach { nestedOption ->
                                    val nestedSubCheckBox = JBCheckBox(nestedOption.toString(), true)
                                    nestedPanel.add(nestedSubCheckBox)
                                    nestedSubCheckBoxesList.add(nestedSubCheckBox)
                                }

                                nestedSubCheckboxes[nestedCheckBox] = nestedSubCheckBoxesList
                                subPanel.add(nestedCheckBox)
                                subPanel.add(nestedPanel)

                                nestedCheckBox.addActionListener {
                                    val isSelected = nestedCheckBox.isSelected
                                    nestedSubCheckBoxesList.forEach { it.isSelected = isSelected }
                                }
                            }
                        }
                    }

                    subCheckboxes[parentCheckBox] = subCheckBoxesList
                    panel.add(subPanel)

                    parentCheckBox.addActionListener {
                        val isSelected = parentCheckBox.isSelected
                        subCheckBoxesList.forEach { subCheckBox ->
                            subCheckBox.isSelected = isSelected
                            nestedSubCheckboxes[subCheckBox]?.forEach { it.isSelected = isSelected }
                        }
                    }
                }
            }
        }

        return panel
    }

    fun getSelectedOptions(): List<String> {
        val selectedOptions = mutableListOf<String>()

        checkBoxes.forEach { checkBox ->
            if (checkBox.isSelected) {
                selectedOptions.add(checkBox.text)

                subCheckboxes[checkBox]?.forEach { subCheckBox ->
                    if (subCheckBox.isSelected) {
                        selectedOptions.add("${checkBox.text}/${subCheckBox.text}")

                        nestedSubCheckboxes[subCheckBox]?.forEach { nestedSubCheckBox ->
                            if (nestedSubCheckBox.isSelected) {
                                selectedOptions.add("${checkBox.text}/${subCheckBox.text}/${nestedSubCheckBox.text}")
                            }
                        }
                    }
                }
            }
        }

        return selectedOptions
    }
}

class CleanArchGenerator : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        val clickedFolder: VirtualFile? = e.getData(CommonDataKeys.VIRTUAL_FILE)

        if (project == null || clickedFolder == null || !clickedFolder.isDirectory) {
            return
        }

        val options = arrayOf(
            Pair(
                "Application",
                arrayOf("DI", "Config")
            ),
            Pair(
                "Data",
                arrayOf(
                    "Service",
                    Pair("DataSource", arrayOf("Remote", "Local")),
                    "Repository", "Network", "Cache"
                )
            ),
            Pair(
                "Domain",
                arrayOf("UseCase", "Entity", "Model", "Repository")
            ),
            Pair(
                "Presentation",
                arrayOf("ViewModel", "Fragment", "Activity", "Component")
            ),
            "Utils"
        )
        val dialog = SelectFoldersDialog(options)

        if (dialog.showAndGet()) {
            val selectedOptions = dialog.getSelectedOptions()

            try {
                WriteCommandAction.runWriteCommandAction(project) {
                    selectedOptions.forEach { folderPath ->
                        createNestedFolders(clickedFolder, folderPath)
                    }
                }
            } catch (ioException: IOException) {
                ioException.printStackTrace()
            }
        }
    }

    private fun createNestedFolders(baseFolder: VirtualFile, folderPath: String) {
        val folderNames = folderPath.split("/")
        var currentFolder = baseFolder

        folderNames.forEach { name ->
            currentFolder = currentFolder.findChild(name.lowercase())
                ?: currentFolder.createChildDirectory(this, name.lowercase())
        }
    }

    override fun update(e: AnActionEvent) {
        val clickedFolder = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = clickedFolder != null && clickedFolder.isDirectory
    }
}