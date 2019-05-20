package com.kodcu.controller; 


import com.esotericsoftware.yamlbeans.YamlException; 
import com.esotericsoftware.yamlbeans.YamlReader; 
import com.esotericsoftware.yamlbeans.YamlWriter; 
import com.kodcu.bean.Config; 
import com.kodcu.bean.RecentFiles; 
import com.kodcu.other.Current; 
import com.kodcu.other.IOHelper; 
import com.kodcu.other.Item; 
import com.kodcu.service.*; 
import com.sun.javafx.application.HostServicesDelegate; 
import de.jensd.fx.fontawesome.AwesomeDude; 
import de.jensd.fx.fontawesome.AwesomeIcon; 
import javafx.application.Platform; 
import javafx.beans.property.SimpleStringProperty; 
import javafx.beans.property.StringProperty; 
import javafx.beans.value.ChangeListener; 
import javafx.collections.FXCollections; 
import javafx.collections.ListChangeListener; 
import javafx.collections.ObservableList; 
import javafx.concurrent.Task; 
import javafx.concurrent.Worker; 
import javafx.event.ActionEvent; 
import javafx.event.Event; 
import javafx.fxml.FXML; 
import javafx.fxml.Initializable; 
import javafx.scene.Node; 
import javafx.scene.Scene; 
import javafx.scene.control.*; 
import javafx.scene.control.cell.TextFieldTreeCell; 
import javafx.scene.image.Image; 
import javafx.scene.image.ImageView; 
import javafx.scene.input.*; 
import javafx.scene.layout.AnchorPane; 
import javafx.scene.layout.Priority; 
import javafx.scene.layout.VBox; 
import javafx.scene.web.WebEngine; 
import javafx.scene.web.WebView; 
import javafx.stage.DirectoryChooser; 
import javafx.stage.FileChooser; 
import javafx.stage.FileChooser.ExtensionFilter; 
import javafx.stage.Stage; 
import net.sourceforge.plantuml.FileFormat; 
import net.sourceforge.plantuml.FileFormatOption; 
import net.sourceforge.plantuml.SourceStringReader; 
import netscape.javascript.JSObject; 
import org.apache.batik.dom.svg.SAXSVGDocumentFactory; 
import org.apache.batik.transcoder.TranscoderException; 
import org.apache.batik.transcoder.TranscoderInput; 
import org.apache.batik.transcoder.TranscoderOutput; 
import org.apache.batik.transcoder.image.PNGTranscoder; 
import org.apache.batik.util.XMLResourceDescriptor; 
import org.slf4j.Logger; 
import org.slf4j.LoggerFactory; 
import org.springframework.beans.factory.annotation.Autowired; 
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext; 
import org.springframework.http.HttpStatus; 
import org.springframework.http.ResponseEntity; 
import org.springframework.stereotype.Controller; 
import org.springframework.web.bind.annotation.PathVariable; 
import org.springframework.web.bind.annotation.RequestMapping; 
import org.springframework.web.bind.annotation.RequestMethod; 
import org.springframework.web.bind.annotation.ResponseBody; 
import org.springframework.web.context.request.async.DeferredResult; 
import org.springframework.web.socket.TextMessage; 
import org.springframework.web.socket.WebSocketSession; 
import org.springframework.web.socket.handler.TextWebSocketHandler; 
import org.w3c.dom.svg.SVGDocument; 

import javax.servlet.http.HttpServletRequest; 
import java.io.*; 
import java.net.URISyntaxException; 
import java.net.URL; 
import java.nio.file.Files; 
import java.nio.file.Path; 
import java.nio.file.Paths; 
import java.security.CodeSource; 
import java.util.*; 
import java.util.concurrent.ExecutorService; 
import java.util.concurrent.Executors; 
import java.util.function.Consumer; 
import java.util.function.Predicate; 
import java.util.function.Supplier; 
import java.util.stream.Collectors; 

import static java.nio.file.StandardOpenOption.*; 


@Controller
public  class  AsciiDocController  extends TextWebSocketHandler  implements Initializable {
	

    private Logger logger = LoggerFactory.getLogger(AsciiDocController.class);
	

    public TabPane tabPane;
	
    public WebView previewView;
	
    public SplitPane splitPane;
	
    public SplitPane splitPaneVertical;
	
    public TreeView<Item> treeView;
	
    public Label splitHideButton;
	
    public Label workingDirButton;
	
    public AnchorPane rootAnchor;
	
    public MenuBar recentFilesBar;
	
    public ProgressBar indikator;
	
    public ListView<String> recentListView;
	
    public MenuItem openFileTreeItem;
	
    public MenuItem openFileListItem;
	
    public MenuItem copyPathTreeItem;
	
    public MenuItem copyPathListItem;
	
    public MenuItem copyTreeItem;
	
    public MenuItem copyListItem;
	
    private WebView mathjaxView;
	

    @Autowired
    private TablePopupController tablePopupController;
	

    @Autowired
    private PathResolverService pathResolver;
	

    @Autowired
    private RenderService renderService;
	

    @Autowired
    private DocBookService docBookController;
	

    @Autowired
    private Html5BookService htmlBookService;
	

    @Autowired
    private FopPdfService fopServiceRunner;
	

    @Autowired
    private Epub3Service epub3Service;
	

    @Autowired
    private Current current;
	

    @Autowired
    private FileBrowseService fileBrowser;
	

    @Autowired
    private IndikatorService indikatorService;
	

    @Autowired
    private KindleMobiService kindleMobiService;
	

    @Autowired
    private SampleBookService sampleBookService;
	

    @Autowired
    private EmbeddedWebApplicationContext server;
	

    @Autowired
    private ParserService parserService;
	

    @Autowired
    private AwesomeService awesomeService;
	

    private ExecutorService singleWorker = Executors.newSingleThreadExecutor();
	
    private ExecutorService threadPollWorker = Executors.newFixedThreadPool(4);
	

    private Stage stage;
	
    private WebEngine previewEngine;
	
    private StringProperty lastRendered = new SimpleStringProperty();
	
    private List<WebSocketSession> sessionList = new ArrayList<>();
	
    private Scene scene;
	
    private AnchorPane tableAnchor;
	
    private Stage tableStage;
	
    private Clipboard clipboard = Clipboard.getSystemClipboard();
	
    private Optional<Path> initialDirectoryy = Optional.ofNullable(null);
	
    private ObservableList<String> recentFiles = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
	
    private AnchorPane configAnchor;
	
    private Stage configStage;
	
    private int tomcatPort = 8080;
	
    private HostServicesDelegate hostServices;
	
    private Path configPath;
	
    private Config config;
	
    private Optional<Path> workingDirectory = Optional.of(Paths.get(System.getProperty("user.home")));
	
    private Optional<File> initialDirectory = Optional.empty();
	
    private List<Optional<Path>> closedPaths = new ArrayList<>();
	

    private List<String> bookNames = Arrays.asList("book.asc", "book.txt", "book.asciidoc", "book.adoc", "book.ad");
	

    private Supplier<Path> workingDirectorySupplier = () -> {

        DirectoryChooser directoryChooser = newDirectoryChooser("Select working directory");
        File file = directoryChooser.showDialog(null);

        workingDirectory = Optional.ofNullable(file.toPath());

        this.workingDirectory.ifPresent(path -> {
            this.fileBrowser.browse(treeView, this, path);
        });

        return Objects.nonNull(file) ? file.toPath() : null;
    };
	

    private DirectoryChooser newDirectoryChooser(String title) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(title);
        initialDirectory.ifPresent(file -> {
            if (Files.isDirectory(file.toPath()))
                directoryChooser.setInitialDirectory(file);
            else
                directoryChooser.setInitialDirectory(file.toPath().getParent().toFile());
        });
        return directoryChooser;
    }
	

    private Consumer<Path> openFileConsumer = path -> {
        if (Files.isDirectory(path))
            return;
        if (pathResolver.isAsciidoc(path))
            this.addTab(path);
        else if (pathResolver.isImage(path))
            this.addImageTab(path);
    };
	

    private Supplier<Path> pathSaveSupplier = () -> {
        FileChooser chooser = newFileChooser("Save Document");
        chooser.getExtensionFilters().add(new ExtensionFilter("Asciidoc", "*.asc", "*.asciidoc", "*.adoc", "*.ad", "*.txt"));
        File file = chooser.showSaveDialog(null);
        return Objects.nonNull(file) ? file.toPath() : null;
    };
	

    private FileChooser newFileChooser(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        initialDirectory.ifPresent(file -> {
            if (Files.isDirectory(file.toPath()))
                fileChooser.setInitialDirectory(file);
            else
                fileChooser.setInitialDirectory(file.toPath().getParent().toFile());
        });

        return fileChooser;
    }
	

    ChangeListener<String> lastRenderedChangeListener = (observableValue, old, nev) -> {
        runSingleTaskLater(task -> {
            sessionList.stream().filter(e -> e.isOpen()).forEach(e -> {
                try {
                    e.sendMessage(new TextMessage(nev));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
        });
    };
	

    @FXML
    private void createTable(Event event) {
        tableStage.show();
    }
	


    @FXML
    private void openConfig(ActionEvent event) {
        configStage.show();
    }
	


    @FXML
    private void fullScreen(ActionEvent event) {

        getStage().setFullScreen(!getStage().isFullScreen());
    }
	

    @FXML
    private void directoryView(ActionEvent event) {
        splitPane.setDividerPositions(0.1610294117647059, 0.5823529411764706);
    }
	


    @FXML
    private void generatePdf(ActionEvent event) {

        Path currentPath = workingDirectory.orElseGet(workingDirectorySupplier);
        docBookController.generateDocbook(previewEngine, currentPath, false);

        runTaskLater((task) -> {
            fopServiceRunner.generateBook(currentPath, configPath);
        });
    }
	

    @FXML
    private void generateSampleBook(ActionEvent event) {

        DirectoryChooser directoryChooser = newDirectoryChooser("Select a New Directory for sample book");
        File file = directoryChooser.showDialog(null);
        runTaskLater((task) -> {
            sampleBookService.produceSampleBook(configPath, file.toPath());
            workingDirectory = Optional.of(file.toPath());
            fileBrowser.browse(treeView, this, file.toPath());
            Platform.runLater(() -> {
                directoryView(null);
                addTab(file.toPath().resolve("book.asc"));
            });
        });
    }
	

    @FXML
    private void convertDocbook(ActionEvent event) {
        Path currentPath = workingDirectory.orElseGet(workingDirectorySupplier);
        docBookController.generateDocbook(previewEngine, currentPath, true);

    }
	

    @FXML
    private void convertEpub(ActionEvent event) throws Exception {

        Path currentPath = workingDirectory.orElseGet(workingDirectorySupplier);
        docBookController.generateDocbook(previewEngine, currentPath, false);

        runTaskLater((task) -> {
            epub3Service.produceEpub3(currentPath, configPath);
        });
    }
	

    public String appendFormula(String fileName, String formula) {

        if (fileName.endsWith(".png")) {
            WebEngine engine = mathjaxView.getEngine();
            engine.executeScript(String.format("appendFormula('%s','%s')", fileName, IOHelper.normalize(formula)));
            return "images/" + fileName;
        }

        return "";

    }
	

    public void svgToPng(String fileName, String svg, String formula,float width,float height) throws IOException, TranscoderException {

        if (!fileName.endsWith(".png") || !svg.startsWith("<svg"))
            return;

        Integer cacheHit = current.getCache().get(fileName);
        int hashCode = fileName.concat(formula).hashCode();
        if (Objects.nonNull(cacheHit))
            if (hashCode == cacheHit)
                return;

        current.getCache().put(fileName, hashCode);

        try (StringReader reader = new StringReader(svg);
             ByteArrayOutputStream ostream = new ByteArrayOutputStream();) {

            String uri = "http://www.w3.org/2000/svg";
            SAXSVGDocumentFactory f = new SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName());
            SVGDocument doc = f.createSVGDocument(uri, reader);

            TranscoderInput transcoderInput = new TranscoderInput(doc);
            TranscoderOutput transcoderOutput = new TranscoderOutput(ostream);

            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH,width);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT,height);
            transcoder.transcode(transcoderInput, transcoderOutput);

            if (!current.currentPath().isPresent())
                saveDoc();

            Path path = current.currentPathParent().get();
            Files.createDirectories(path.resolve("images"));

            Files.write(path.resolve("images/").resolve(fileName), ostream.toByteArray(), CREATE, WRITE, TRUNCATE_EXISTING);

            lastRenderedChangeListener.changed(null, lastRendered.getValue(), lastRendered.getValue());

        }

    }
	

    
	

    public <T> void runTaskLater(Consumer<Task<T>> consumer) {

        Task<T> task = new Task<T>() {
            @Override
            protected T call() throws Exception {
                consumer.accept(this);
                return null;
            }
        };

        threadPollWorker.submit(task);
    }
	

    public <T> void runSingleTaskLater(Consumer<Task<T>> consumer) {

        Task<T> task = new Task<T>() {
            @Override
            protected T call() throws Exception {
                consumer.accept(this);
                return null;
            }
        };

        singleWorker.submit(task);
    }
	

    @FXML
    private void convertMobi(ActionEvent event) throws Exception {


        Path currentPath = workingDirectory.orElseGet(workingDirectorySupplier);

        if (Objects.nonNull(config.getKindlegenDir())) {
            if (!Files.exists(Paths.get(config.getKindlegenDir()))) {
                config.setKindlegenDir(null);
            }
        }

        if (Objects.isNull(config.getKindlegenDir())) {
            FileChooser fileChooser = newFileChooser("Select 'kindlegen' executable");
            File kindlegenFile = fileChooser.showOpenDialog(null);
            if (Objects.isNull(kindlegenFile))
                return;

            config.setKindlegenDir(kindlegenFile.toPath().getParent().toString());

        }

        runTaskLater((task) -> {
            epub3Service.produceEpub3(currentPath, configPath);
            kindleMobiService.produceMobi(currentPath, config.getKindlegenDir());
        });

    }
	

    @FXML
    private void generateHtml(ActionEvent event) {

        Path currentPath = workingDirectory.orElseGet(workingDirectorySupplier);

        htmlBookService.produceXhtml5(previewEngine, currentPath, configPath);
    }
	

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        try {
            CodeSource codeSource = AsciiDocController.class.getProtectionDomain().getCodeSource();
            File jarFile = new File(codeSource.getLocation().toURI().getPath());
            configPath = jarFile.toPath().getParent().getParent().resolve("conf");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        loadConfigurations();
        loadRecentFileList();

        recentListView.setItems(recentFiles);
        recentFiles.addListener((ListChangeListener<String>) c -> {
            recentListView.visibleProperty().setValue(c.getList().size() > 0);
            recentListView.getSelectionModel().selectFirst();
        });

        recentListView.setOnMouseClicked(event -> {
            if (event.getClickCount() > 1) {
                openRecentListFile(event);
            }
        });

        treeView.setCellFactory(param -> {
            TreeCell<Item> cell = new TextFieldTreeCell<Item>();
            cell.setOnDragDetected(event -> {
                Dragboard db = cell.startDragAndDrop(TransferMode.ANY);
                ClipboardContent content = new ClipboardContent();
                content.putFiles(Arrays.asList(cell.getTreeItem().getValue().getPath().toFile()));
                db.setContent(content);
            });
            return cell;
        });

        tomcatPort = server.getEmbeddedServletContainer().getPort();

        lastRendered.addListener(lastRenderedChangeListener);

        // MathJax
        mathjaxView = new WebView();
        mathjaxView.setVisible(false);
        rootAnchor.getChildren().add(mathjaxView);

        WebEngine mathjaxEngine = mathjaxView.getEngine();
        mathjaxEngine.getLoadWorker().stateProperty().addListener((observableValue1, state, state2) -> {
            JSObject window = (JSObject) mathjaxEngine.executeScript("window");
            if (Objects.isNull(window.getMember("app"))) ;
            window.setMember("app", this);
        });
        //

        mathjaxView.getEngine().load(String.format("http://localhost:%d/mathjax.html", tomcatPort));

        previewEngine = previewView.getEngine();
        previewEngine.load(String.format("http://localhost:%d/index.html", tomcatPort));

        previewEngine.getLoadWorker().stateProperty().addListener((observableValue1, state, state2) -> {
            JSObject window = (JSObject) previewEngine.executeScript("window");
            if (Objects.isNull(window.getMember("app"))) ;
            window.setMember("app", this);
        });

        previewEngine.getLoadWorker().exceptionProperty().addListener((ov, t, t1) -> {
            t1.printStackTrace();
        });


        /// Treeview

        if (Objects.nonNull(config.getWorkingDirectory()))
            workingDirectory = Optional.ofNullable(Paths.get(config.getWorkingDirectory()));

        Path workDir = workingDirectory.orElse(Paths.get(System.getProperty("user.home")));
//
        fileBrowser.browse(treeView, this, workDir);

        //

        AwesomeDude.setIcon(workingDirButton, AwesomeIcon.FOLDER_ALT, "14.0");
        AwesomeDude.setIcon(splitHideButton, AwesomeIcon.CHEVRON_LEFT, "14.0");

        tabPane.getTabs().addListener((ListChangeListener<Tab>) c -> {
            if (tabPane.getTabs().isEmpty())
                runActionLater(this::newDoc);
        });

        openFileTreeItem.setOnAction(event -> {
            Path path = getSelectedTabPath();
            openFileConsumer.accept(path);
        });

        openFileListItem.setOnAction(this::openRecentListFile);

        copyPathTreeItem.setOnAction(event -> {
            Path path = getSelectedTabPath();
            this.cutCopy(path.toString());
        });

        copyPathListItem.setOnAction(event -> {
            this.cutCopy(recentListView.getSelectionModel().getSelectedItem());
        });

        copyTreeItem.setOnAction(event -> {
            Path path = getSelectedTabPath();
            this.copyFile(path);
        });

        copyListItem.setOnAction(event -> {
            Path path = Paths.get(recentListView.getSelectionModel().getSelectedItem());
            this.copyFile(path);
        });

        treeView.setOnMouseClicked(event -> {
            TreeItem<Item> selectedItem = treeView.getSelectionModel().getSelectedItem();
            if (Objects.isNull(selectedItem))
                return;
            Path selectedPath = selectedItem.getValue().getPath();
            if (event.getButton() == MouseButton.PRIMARY)
                if (Files.isDirectory(selectedPath)) {
                    try {
                        if (selectedItem.getChildren().size() == 0) {
                            final List<Path> files = new LinkedList<>();
                            Files.newDirectoryStream(selectedPath).forEach(path -> {
                                if (pathResolver.isHidden(path))
                                    return;

                                if (pathResolver.isViewable(path))
<<<<<<< C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var1_543942865178441392.java
                                    selectedItem.getChildren().add(new TreeItem<>(new Item(path), awesomeService.getIcon(path)));
=======
                                    files.add(path);
>>>>>>> C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var2_8398538748043818670.java
                            });
                            Collections.sort(files);
                            files.forEach(path -> {
                                selectedItem.getChildren().add(new TreeItem<>(new Item(path),awesomeService.getIcon(path)));
                            });
                        }
                        selectedItem.setExpanded(!selectedItem.isExpanded());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (event.getClickCount() > 1) {
                    openFileConsumer.accept(selectedPath);
                }
        });

        runActionLater(this::newDoc);

    }
	

    private void addImageTab(Path imagePath) {
        Tab tab = createTab();
        Label label = (Label) tab.getGraphic();
        label.setText(imagePath.getFileName().toString());
        ImageView imageView = new ImageView(new Image(IOHelper.pathToUrl(imagePath)));
        imageView.setPreserveRatio(true);
        imageView.fitWidthProperty().bind(tabPane.widthProperty());

        tab.setContent(imageView);
        current.putTab(tab, imagePath, current.currentView());
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }
	

    private void openRecentListFile(Event event) {
        Path path = Paths.get(recentListView.getSelectionModel().getSelectedItem());

        if (pathResolver.isAsciidoc(path))
            addTab(path);
        else
            getHostServices().showDocument(path.toUri().toString());
    }
	

    private Path getSelectedTabPath() {
        TreeItem<Item> selectedItem = treeView.getSelectionModel().getSelectedItem();
        Item value = selectedItem.getValue();
        Path path = value.getPath();
        return path;
    }
	

    private void runActionLater(Consumer<ActionEvent> consumer) {
        Platform.runLater(() -> {
            consumer.accept(null);
        });
    }
	

    private void loadConfigurations() {
        try {
            YamlReader yamlReader =
                    new YamlReader(new FileReader(configPath.resolve("config.yml").toFile()));
            yamlReader.getConfig().setClassTag("Config", Config.class);
            config = yamlReader.read(Config.class);

        } catch (YamlException | FileNotFoundException e) {
            e.printStackTrace();
        }

        if (!config.getDirectoryPanel())
            Platform.runLater(() -> {
                splitPane.setDividerPositions(0, 0.51);
            });

    }
	

    private void loadRecentFileList() {

        try {
            YamlReader yamlReader =
                    new YamlReader(new FileReader(configPath.resolve("recentFiles.yml").toFile()));
            yamlReader.getConfig().setClassTag("RecentFiles", RecentFiles.class);
            RecentFiles readed = yamlReader.read(RecentFiles.class);

            recentFiles.addAll(readed.getFiles());
        } catch (YamlException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
	

    public void externalBrowse() {

        hostServices.showDocument(String.format("http://localhost:%d/index.html", tomcatPort));
    }
	

    @FXML
    public void changeWorkingDir(Event actionEvent) {
        DirectoryChooser directoryChooser = newDirectoryChooser("Select Working Directory");
        File selectedDir = directoryChooser.showDialog(null);
        if (Objects.nonNull(selectedDir)) {
            config.setWorkingDirectory(selectedDir.toString());
            workingDirectory = Optional.of(selectedDir.toPath());
            fileBrowser.browse(treeView, this, selectedDir.toPath());
            initialDirectory = Optional.ofNullable(selectedDir);

        }
    }
	

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessionList.add(session);
        String value = lastRendered.getValue();
        if (Objects.nonNull(value))
            session.sendMessage(new TextMessage(value));

    }
	

    @FXML
    public void closeApp(ActionEvent event) throws IOException {

        File recentFileYml = configPath.resolve("recentFiles.yml").toFile();
        YamlWriter yamlWriter = new YamlWriter(new FileWriter(recentFileYml));
        yamlWriter.getConfig().setClassTag("RecentFiles", RecentFiles.class);
        yamlWriter.write(new RecentFiles(recentFiles));
        yamlWriter.close();

        //

        File configYml = configPath.resolve("config.yml").toFile();
        yamlWriter = new YamlWriter(new FileWriter(configYml));
        yamlWriter.getConfig().setClassTag("Config", Config.class);
        yamlWriter.write(config);
        yamlWriter.close();

    }
	

    @FXML
    private void openDoc(Event event) {
        FileChooser fileChooser = newFileChooser("Open Asciidoc File");
        fileChooser.getExtensionFilters().add(new ExtensionFilter("Asciidoc", "*.asc", "*.asciidoc", "*.adoc", "*.ad", "*.txt"));
        fileChooser.getExtensionFilters().add(new ExtensionFilter("All", "*.*"));
        List<File> chosenFiles = fileChooser.showOpenMultipleDialog(stage);
        if (chosenFiles != null) {
            chosenFiles.stream().map(e -> e.toPath()).forEach(this::addTab);
            chosenFiles.stream()
                    .map(File::toString).filter(file -> !recentFiles.contains(file))
