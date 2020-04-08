package umm3601.note;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.mockrunner.mock.web.MockHttpServletRequest;
import com.mockrunner.mock.web.MockHttpServletResponse;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.util.ContextUtil;
import io.javalin.plugin.json.JavalinJson;
import umm3601.JwtProcessor;
import umm3601.UnprocessableResponse;

public class NoteControllerSpec {

  MockHttpServletRequest mockReq = new MockHttpServletRequest();
  MockHttpServletResponse mockRes = new MockHttpServletResponse();

  @Mock(name = "dt")
  DeathTimer dtMock;

  private ObjectId samsNoteId;

  static MongoClient mongoClient;
  @Spy
  static MongoDatabase db;

  @Mock(name = "jwtProcessor")
  JwtProcessor jwtProcessorMock;

  private void useJwtForDoorBoard1() {
    // Make a fake DecodedJWT for jwtProcessorMock to return.
    // (Sam's doorBoard ID is "doorBoard3_ID".)
    DecodedJWT mockDecodedJWT = Mockito.mock(DecodedJWT.class);
    when(mockDecodedJWT.getSubject()).thenReturn("doorBoard1_ID");
    when(jwtProcessorMock.verifyJwtFromHeader(any()))
      .thenReturn(mockDecodedJWT);
  }

  private void useJwtForSam() {
    // Make a fake DecodedJWT for jwtProcessorMock to return.
    // (Sam's doorBoard ID is "doorBoard3_ID".)
    DecodedJWT mockDecodedJWT = Mockito.mock(DecodedJWT.class);
    when(mockDecodedJWT.getSubject()).thenReturn("doorBoard3_ID");
    when(jwtProcessorMock.verifyJwtFromHeader(any()))
      .thenReturn(mockDecodedJWT);
  }

  private void useJwtForNewUser() {
    DecodedJWT mockDecodedJWT = Mockito.mock(DecodedJWT.class);
    when(mockDecodedJWT.getSubject()).thenReturn("e7fd674c72b76596c75d9f1e");
    when(jwtProcessorMock.verifyJwtFromHeader(any()))
      .thenReturn(mockDecodedJWT);
  }

  private void useInvalidJwt() {
    when(jwtProcessorMock.verifyJwtFromHeader(any()))
      .thenThrow(new UnauthorizedResponse());
  }



  @InjectMocks
  NoteController noteController;

  static ObjectMapper jsonMapper = new ObjectMapper();

  @BeforeAll
  public static void setupAll() {
    String mongoAddr = System.getenv().getOrDefault("MONGO_ADDR", "localhost");
    mongoClient = MongoClients.create(MongoClientSettings.builder()
        .applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(mongoAddr)))).build());

    db = mongoClient.getDatabase("test");
  }

  @BeforeEach
  public void setupEach() throws IOException {
    MockitoAnnotations.initMocks(this);
    // Reset our mock objects
    mockReq.resetAll();
    mockRes.resetAll();
    when(dtMock.updateTimerStatus(any(Note.class))).thenReturn(true);

    MongoCollection<Document> noteDocuments = db.getCollection("notes");
    noteDocuments.drop();
    List<Document> testNotes = new ArrayList<>();
    testNotes.add(Document.parse("{ "
      + "doorBoardID: \"doorBoard1_ID\", "
      + "body: \"I am running 5 minutes late to my non-existent office\", "
      + "addDate: \"2020-03-07T22:03:38+0000\", "
      + "expireDate: \"2021-03-20T22:03:38+0000\", "
      + "status: \"active\""
      + "}"));
    testNotes.add(Document.parse("{ "
      + "doorBoardID: \"doorBoard1_ID\", "
      + "body: \"I am never coming to my office again\", "
      + "addDate: \"2020-03-07T22:03:38+0000\", "
      + "expireDate: \"2099-03-07T22:03:38+0000\", "
      + "status: \"active\""
      + "}"));
    testNotes.add(Document.parse("{ "
      + "doorBoardID: \"doorBoard2_ID\", "
      + "body: \"I am on sabbatical no office hours\", "
      + "addDate: \"2020-03-07T22:03:38+0000\", "
      + "expireDate: \"2021-03-07T22:03:38+0000\", "
      + "status: \"active\""
      + "}"));
    testNotes.add(Document.parse("{ "
      + "doorBoardID: \"doorBoard2_ID\", "
      + "body: \"Go to doorBoard3's office\", "
      + "addDate: \"2020-03-07T22:03:38+0000\", "
      + "expireDate: \"2020-03-21T22:03:38+0000\", "
      + "status: \"active\""
      + "}"));
    testNotes.add(Document.parse("{ "
      + "doorBoardID: \"doorBoard3_ID\", "
      + "body: \"Not many come to my office I offer donuts\", "
      + "addDate: \"2020-03-07T22:03:38+0000\", "
      + "expireDate: \"2021-03-07T22:03:38+0000\", "
      + "status: \"active\""
      + "}"));
    samsNoteId = new ObjectId();
    BasicDBObject sam = new BasicDBObject("_id", samsNoteId);
    sam = sam.append("doorBoardID", "doorBoard3_ID")
      .append("body", "I am sam")
      .append("addDate", "2020-03-07T22:03:38+0000")
      .append("expireDate", "2100-03-07T22:03:38+0000")
      .append("status", "active");

    noteDocuments.insertMany(testNotes);
    noteDocuments.insertOne(Document.parse(sam.toJson()));
  }

  @AfterAll
  public static void teardown() {
    db.drop();
    mongoClient.close();
  }

  /*
   * Tests for GET api/notes when you're logged in with the right credentials.
   */

  @Test
  public void getAllNotesForDoorBoard1() {
    mockReq.setQueryString("doorBoardid=doorBoard1_ID");

    useJwtForDoorBoard1();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    noteController.getNotesByDoorBoard(ctx);

    assertEquals(200, mockRes.getStatus());

    String result = ctx.resultString();
    Note[] resultNotes = JavalinJson.fromJson(result, Note[].class);

    assertEquals(2, resultNotes.length);
    for (Note note : resultNotes) {
      assertEquals("doorBoard1_ID", note.doorBoardID, "Incorrect ID");
    }
  }

  @Test
  public void getDraftNotesForDoorBoard1() {
    mockReq.setQueryString("doorBoardid=doorBoard1_ID&status=draft");

    useJwtForDoorBoard1();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    noteController.getNotesByDoorBoard(ctx);

    assertEquals(200, mockRes.getStatus());

    String result = ctx.resultString();
    Note[] resultNotes = JavalinJson.fromJson(result, Note[].class);

    assertEquals(0, resultNotes.length);
  }


  @Test
  public void getActiveNotesForDoorBoard1() {
    mockReq.setQueryString("doorBoardid=doorBoard1_ID&status=active");

    useJwtForDoorBoard1();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    noteController.getNotesByDoorBoard(ctx);

    assertEquals(200, mockRes.getStatus());

    String result = ctx.resultString();
    Note[] resultNotes = JavalinJson.fromJson(result, Note[].class);

    assertEquals(2, resultNotes.length);
    for (Note note : resultNotes) {
      assertEquals("doorBoard1_ID", note.doorBoardID, "Incorrect ID");
    }
  }

  /*
   * Tests for GET api/notes when you aren't logged in.
   */

  @Test
  public void getAllNotesForDoorBoard1WithoutJwtFails() {
    mockReq.setQueryString("doorBoardid=doorBoard1_ID");

    useInvalidJwt();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    assertThrows(UnauthorizedResponse.class, () -> {
      noteController.getNotesByDoorBoard(ctx);
    });
  }

  @Test
  public void getDraftNotesForDoorBoard1WithoutJwtFails() {
    mockReq.setQueryString("doorBoardid=doorBoard1_ID&status=draft");

    useInvalidJwt();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    assertThrows(UnauthorizedResponse.class, () -> {
      noteController.getNotesByDoorBoard(ctx);
    });
  }

  // Active notes are public.
  // You're allowed to see them even if you aren't logged in.
  @Test
  public void getActiveNotesForDoorBoard1WithoutJwtIsFine() {
    mockReq.setQueryString("doorBoardid=doorBoard1_ID&status=active");

    useInvalidJwt();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    noteController.getNotesByDoorBoard(ctx);

    assertEquals(200, mockRes.getStatus());

    String result = ctx.resultString();
    Note[] resultNotes = JavalinJson.fromJson(result, Note[].class);

    assertEquals(2, resultNotes.length);
    for (Note note : resultNotes) {
      assertEquals("doorBoard1_ID", note.doorBoardID, "Incorrect ID");
    }
  }

  /*
   * Tests for GET api/notes when you're logged in as a different doorBoard.
   */

  @Test
  public void getAllNotesForDoorBoard1LoggedInAsWrongDoorBoardFails() {
    mockReq.setQueryString("doorBoardid=doorBoard1_ID");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    assertThrows(ForbiddenResponse.class, () -> {
      noteController.getNotesByDoorBoard(ctx);
    });
  }

  @Test
  public void getDraftNotesForDoorBoard1LoggedInAsWrongDoorBoardFails() {
    mockReq.setQueryString("doorBoardid=doorBoard1_ID&status=draft");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    assertThrows(ForbiddenResponse.class, () -> {
      noteController.getNotesByDoorBoard(ctx);
    });
  }


  @Test
  public void getActiveNotesForDoorBoard1LoggedInAsWrongDoorBoardIsFine() {
    mockReq.setQueryString("doorBoardid=doorBoard1_ID&status=active");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    noteController.getNotesByDoorBoard(ctx);

    assertEquals(200, mockRes.getStatus());

    String result = ctx.resultString();
    Note[] resultNotes = JavalinJson.fromJson(result, Note[].class);

    assertEquals(2, resultNotes.length);
    for (Note note : resultNotes) {
      assertEquals("doorBoard1_ID", note.doorBoardID, "Incorrect ID");
    }
  }

  /*
   * Tests for GET api/notes without the doorBoardid query parameter.
   *
   * This is always allowed if you specify status=active; anyone is allowed
   * to view any active notes.
   *
   * Without status=active, it's always forbidden; no-one can view *all* notes
   * in the database, including draft, deleted, and template notes. It doesn't
   * matter who you're logged in as; you can still only see your own stuff.
   */

  @Test
  public void getAllNotesInTheDatabaseFailsWithoutAJwt() {
    mockReq.setQueryString("");

    useInvalidJwt();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    assertThrows(UnauthorizedResponse.class, () -> {
      noteController.getNotesByDoorBoard(ctx);
    });
  }

  @Test
  public void getAllNotesInTheDatabaseFailsEvenWithAJwt() {
    mockReq.setQueryString("");

    useJwtForDoorBoard1();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    assertThrows(ForbiddenResponse.class, () -> {
      noteController.getNotesByDoorBoard(ctx);
    });
  }

  @Test
  public void getAllActiveNotesInTheDatabaseIsFineEvenWithoutJwt() {
    mockReq.setQueryString("status=active");

    useInvalidJwt();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    noteController.getNotesByDoorBoard(ctx);

    assertEquals(200, mockRes.getStatus());

    String result = ctx.resultString();
    Note[] resultNotes = JavalinJson.fromJson(result, Note[].class);

    assertEquals(6, resultNotes.length);
  }

  /*
   * Tests for adding notes.
   */


  @Test
  public void addNote() throws IOException {
    ArgumentCaptor<Note> noteCaptor = ArgumentCaptor.forClass(Note.class);
    String testNewNote = "{ "
      + "\"body\": \"Test Body\", "
      + "\"addDate\": \"2020-03-07T22:03:38+0000\", "
      + "\"expireDate\": \"2021-03-07T22:03:38+0000\", "
      + "\"status\": \"active\""
      + "}";

    mockReq.setBodyContent(testNewNote);
    mockReq.setMethod("POST");

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/new");

    DecodedJWT mockDecodedJWT = Mockito.mock(DecodedJWT.class);
    when(mockDecodedJWT.getSubject()).thenReturn("some_new_doorBoard");

    when(jwtProcessorMock.verifyJwtFromHeader(any()))
      .thenReturn(mockDecodedJWT);

    noteController.addNewNote(ctx);

    assertEquals(201, mockRes.getStatus());

    String result = ctx.resultString();
    String id = jsonMapper.readValue(result, ObjectNode.class).get("id").asText();
    assertNotEquals("", id);

    assertEquals(1, db.getCollection("notes").countDocuments(eq("_id", new ObjectId(id))));

    Document addedNote = db.getCollection("notes").find(eq("_id", new ObjectId(id))).first();
    assertNotNull(addedNote);
    assertEquals("some_new_doorBoard", addedNote.getString("doorBoardID"));
    assertEquals("Test Body", addedNote.getString("body"));
    assertEquals("2020-03-07T22:03:38+0000", addedNote.getString("addDate"));
    assertEquals("2021-03-07T22:03:38+0000", addedNote.getString("expireDate"));
    assertEquals("active", addedNote.getString("status"));

    verify(dtMock).updateTimerStatus(noteCaptor.capture());
    Note newNote = noteCaptor.getValue();
    assertEquals(id, newNote._id);
    assertEquals("some_new_doorBoard", newNote.doorBoardID);
    assertEquals("Test Body", newNote.body);
    assertEquals("2020-03-07T22:03:38+0000", newNote.addDate);
    assertEquals("2021-03-07T22:03:38+0000", newNote.expireDate);
    assertEquals("active", newNote.status);
  }

  @Test
  public void addNoteWithInvalidJwtFails() throws IOException {
    String testNewNote = "{ "
      + "\"body\": \"Faily McFailface\", "
      + "\"addDate\": \"2020-03-07T22:03:38+0000\", "
      + "\"expireDate\": \"2021-03-07T22:03:38+0000\", "
      + "\"status\": \"active\""
      + "}";

    mockReq.setBodyContent(testNewNote);
    mockReq.setMethod("POST");

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/new");

    useInvalidJwt();

    assertThrows(UnauthorizedResponse.class, () -> {
      noteController.addNewNote(ctx);
    });

    assertEquals(0, db.getCollection("notes").countDocuments(eq("body", "Faily McFailface")));
  }

  // Our API doesn't allow setting the doorBoard of a note in the request body
  // (if we did, you could just post notes for whoever you want.)
  // Instead, we get the doorBoard's ID from the JWT that Auth0 gives us,
  // and attach the new note to that user.
  @Test
  public void addNoteWithDoorBoardIdInTheRequestBodyFails() throws IOException {
    String testNewNote = "{ "
      + "\"doorBoardID\": \"e7fd674c72b76596c75d9f1e\", "
      + "\"body\": \"Faily McFailface\", "
      + "\"addDate\": \"2020-03-07T22:03:38+0000\", "
      + "\"expireDate\": 2021-03-07T22:03:38+0000, "
      + "\"status\": \"active\""
      + "}";

    mockReq.setBodyContent(testNewNote);
    mockReq.setMethod("POST");

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/new");

    // Put the doorBoard ID in the JWT, just to try to trick NoteController.
    DecodedJWT mockDecodedJWT = Mockito.mock(DecodedJWT.class);
    when(mockDecodedJWT.getSubject()).thenReturn("e7fd674c72b76596c75d9f1e");

    when(jwtProcessorMock.verifyJwtFromHeader(any()))
      .thenReturn(mockDecodedJWT);

    assertThrows(BadRequestResponse.class, () -> {
      noteController.addNewNote(ctx);
    });

    assertEquals(0, db.getCollection("notes").countDocuments(eq("body", "Faily McFailface")));
  }

  @Test
  public void AddNoteWithoutExpiration() throws IOException {
    String testNewNote = "{ "
      + "\"body\": \"Test Body\", "
      + "\"addDate\": \"2020-03-07T22:03:38+0000\", "
      + "\"status\": \"active\""
      + "}";


    mockReq.setBodyContent(testNewNote);
    mockReq.setMethod("POST");

    useJwtForNewUser();


    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/new");

    noteController.addNewNote(ctx);

    assertEquals(201, mockRes.getStatus());

    String result = ctx.resultString();
    String id = jsonMapper.readValue(result, ObjectNode.class).get("id").asText();
    assertNotEquals("", id);

    assertEquals(1, db.getCollection("notes").countDocuments(eq("_id", new ObjectId(id))));

    Document addedNote = db.getCollection("notes").find(eq("_id", new ObjectId(id))).first();
    assertNotNull(addedNote);
    assertEquals("e7fd674c72b76596c75d9f1e", addedNote.getString("doorBoardID"));
    assertEquals("Test Body", addedNote.getString("body"));
    assertEquals("2020-03-07T22:03:38+0000", addedNote.getString("addDate"));
    assertNull(addedNote.getString("expireDate"));
    assertEquals("active", addedNote.getString("status"));
    verify(dtMock, never()).updateTimerStatus(any(Note.class));
  }

  @Test
  public void AddNewInactiveWithExpiration() throws IOException {
    String testNewNote = "{ "
      + "\"body\": \"Test Body\", "
      + "\"addDate\": \"2020-03-07T22:03:38+0000\", " + "\"expireDate\": \"2021-03-07T22:03:38+0000\", "
      + "\"status\": \"draft\""
      + "}";

    mockReq.setBodyContent(testNewNote);
    mockReq.setMethod("POST");

    useJwtForNewUser();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/new");

    assertThrows(ConflictResponse.class, () -> {
      noteController.addNewNote(ctx);
    });
  }

  /*
   * Tests for deleting notes.
   */

  @Test
  public void deleteNote() throws IOException {
    mockReq.setMethod("DELETE");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));
    noteController.deleteNote(ctx);

    assertEquals(204, mockRes.getStatus());

    assertEquals(0, db.getCollection("notes").countDocuments(eq("_id", samsNoteId)));
    // Make sure we stop the Death Timer
    verify(dtMock).clearKey(anyString());
  }

  @Test
  public void deleteNoteWithoutJwtFails() throws IOException {
    mockReq.setMethod("DELETE");

    useInvalidJwt();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));
    assertThrows(UnauthorizedResponse.class, () -> {
      noteController.deleteNote(ctx);
    });

    // Make sure that the database is unchanged
    assertEquals(1, db.getCollection("notes").countDocuments(eq("_id", samsNoteId)));
    verify(dtMock, never()).clearKey(anyString());
  }

  @Test
  public void deleteNoteLoggedInAsWrongUserFails() throws IOException {
    mockReq.setMethod("DELETE");

    useJwtForNewUser();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));
    assertThrows(ForbiddenResponse.class, () -> {
      noteController.deleteNote(ctx);
    });

    // Make sure that the database is unchanged
    assertEquals(1, db.getCollection("notes").countDocuments(eq("_id", samsNoteId)));
    verify(dtMock, never()).clearKey(anyString());
  }


  /*
   * Tests for editing notes.
   */

  @Test
  public void editSingleField() throws IOException {
    String reqBody = "{\"body\": \"I am not sam anymore\"}";
    mockReq.setBodyContent(reqBody);
    mockReq.setMethod("PATCH");
    // Because we're partially altering an object, we make a body with just the
    // alteration and use the PATCH (not PUT) method

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));
    noteController.editNote(ctx);

    assertEquals(204, mockRes.getStatus());
    // We don't have a good way to return just the edited object right now,
    // so we return nothing in the body and show that with a 204 response.

    assertEquals(1, db.getCollection("notes").countDocuments(eq("_id", samsNoteId)));
    // There should still be exactly one note per id, and the id shouldn't have
    // changed.

    Document editedNote = db.getCollection("notes").find(eq("_id", samsNoteId)).first();
    assertNotNull(editedNote);
    // The note should still actually exist

    assertEquals("I am not sam anymore", editedNote.getString("body"));
    // The edited field should show the new value

    assertEquals("doorBoard3_ID", editedNote.getString("doorBoardID"));
    assertEquals("active", editedNote.getString("status"));
    assertEquals("2020-03-07T22:03:38+0000", editedNote.getString("addDate"));
    assertEquals("2100-03-07T22:03:38+0000", editedNote.getString("expireDate"));
    // all other fields should be untouched

    verify(dtMock).updateTimerStatus(any(Note.class));
  }

  @Test
  public void editMultipleFields() throws IOException {
    ArgumentCaptor<Note> noteCaptor = ArgumentCaptor.forClass(Note.class);
    String reqBody = "{\"body\": \"I am still sam\", \"expireDate\": \"2025-03-07T22:03:38+0000\"}";
    mockReq.setBodyContent(reqBody);
    mockReq.setMethod("PATCH");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));
    noteController.editNote(ctx);

    assertEquals(204, mockRes.getStatus());

    assertEquals(1, db.getCollection("notes").countDocuments(eq("_id", samsNoteId)));

    Document editedNote = db.getCollection("notes").find(eq("_id", samsNoteId)).first();
    assertNotNull(editedNote);

    assertEquals("I am still sam", editedNote.getString("body"));
    assertEquals("2025-03-07T22:03:38+0000", editedNote.getString("expireDate"));

    assertEquals("active", editedNote.getString("status"));
    assertEquals("doorBoard3_ID", editedNote.getString("doorBoardID"));
    assertEquals("2020-03-07T22:03:38+0000", editedNote.getString("addDate"));

    // Since the expireDate was changed, the timer's status should have been updated
    verify(dtMock).updateTimerStatus(noteCaptor.capture());
    Note updatedNote = noteCaptor.getValue();
    assertEquals(samsNoteId.toHexString(), updatedNote._id);
    assertEquals("I am still sam", updatedNote.body);
    assertEquals("2025-03-07T22:03:38+0000", updatedNote.expireDate);
    assertEquals("active", updatedNote.status);
    assertEquals("doorBoard3_ID", updatedNote.doorBoardID);
    assertEquals("2020-03-07T22:03:38+0000", updatedNote.addDate);
  }

  @Test
  public void editWithoutJwtFails() throws IOException {
    String reqBody = "{\"body\": \"I am not sam anymore\"}";
    mockReq.setBodyContent(reqBody);
    mockReq.setMethod("PATCH");

    useInvalidJwt();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));
    assertThrows(UnauthorizedResponse.class, () -> {
      noteController.editNote(ctx);
    });

    // Make sure the note was not changed.
    Document samsNote = db.getCollection("notes").find(eq("_id", samsNoteId)).first();
    assertEquals("I am sam", samsNote.getString("body"));
  }

  @Test
  public void editLoggedInAsWrongDoorBoardFails() throws IOException {
    String reqBody = "{\"body\": \"I am not sam anymore\"}";
    mockReq.setBodyContent(reqBody);
    mockReq.setMethod("PATCH");

    useJwtForNewUser();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));
    assertThrows(ForbiddenResponse.class, () -> {
      noteController.editNote(ctx);
    });

    // Make sure the note was not changed.
    Document samsNote = db.getCollection("notes").find(eq("_id", samsNoteId)).first();
    assertEquals("I am sam", samsNote.getString("body"));
  }


  @Test
  public void editMissingId() throws IOException {
    String reqBody = "{\"body\": \"I am not sam anymore\"}";
    mockReq.setBodyContent(reqBody);
    mockReq.setMethod("PATCH");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id",
        ImmutableMap.of("id", "58af3a600343927e48e87335"));

    assertThrows(NotFoundResponse.class, () -> {
      noteController.editNote(ctx);
    });
  }

  @Test
  public void editBadId() throws IOException {
    String reqBody = "{\"body\": \"I am not sam anymore\"}";
    mockReq.setBodyContent(reqBody);
    mockReq.setMethod("PATCH");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id",
        ImmutableMap.of("id", "this garbage isn't an id!"));

    assertThrows(BadRequestResponse.class, () -> {
      noteController.editNote(ctx);
    });
  }

  @Test
  public void editIdWithMalformedBody() throws IOException {
    mockReq.setBodyContent("This isn't parsable as a document");
    mockReq.setMethod("PATCH");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));

    assertThrows(BadRequestResponse.class, () -> {
      noteController.editNote(ctx);
    });
  }

  @Test
  public void editIdWithInvalidValue() throws IOException {
    mockReq.setBodyContent("{\"expireDate\": \"not actually a date\"}");
    mockReq.setMethod("PATCH");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));

    assertThrows(UnprocessableResponse.class, () -> {
      noteController.editNote(ctx);
    });
    // HTTP 422 Unprocessable Entity: the entity could be syntactically parsed but
    // was semantically garbage.
    // In this case, it's because a non-date-string was attempted to be inserted
    // into a location that requires
    // a date string.
  }

  @Test
  public void editIdWithBadKeys() throws IOException {
    mockReq.setBodyContent("{\"badKey\": \"irrelevant value\"}");
    mockReq.setMethod("PATCH");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));

    assertThrows(ConflictResponse.class, () -> {
      noteController.editNote(ctx);
    });
    // ConflictResponse represents a 409 error, in this case an attempt to edit a
    // nonexistent field.
  }

  @Test
  public void editIdWithIllegalKeys() throws IOException {
    mockReq.setBodyContent("{\"doorBoardID\": \"Charlie\"}");
    mockReq.setMethod("PATCH");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));

    assertThrows(BadRequestResponse.class, () -> {
      noteController.editNote(ctx);
    });
  }

  // The 422 and 409 errors could be switched between these conditions, or they
  // could possibly both be 409?
  // Additionally, should attempting to edit a non-editable field (id, doorBoardID, or
  // addDate) throw a 422, 409, 400, or 403?

  @Test
  public void RemoveExpirationFromNote() throws IOException {
    ArgumentCaptor<Note> noteCaptor = ArgumentCaptor.forClass(Note.class);
    mockReq.setBodyContent("{\"expireDate\": null}");
    mockReq.setMethod("PATCH");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));

    noteController.editNote(ctx);

    assertEquals(204, mockRes.getStatus());

    assertEquals(1, db.getCollection("notes").countDocuments(eq("_id", samsNoteId)));

    Document editedNote = db.getCollection("notes").find(eq("_id", samsNoteId)).first();
    assertNotNull(editedNote);

    assertNull(editedNote.getString("expireDate"));

    assertEquals("active", editedNote.getString("status"));
    assertEquals("I am sam", editedNote.getString("body"));
    assertEquals("doorBoard3_ID", editedNote.getString("doorBoardID"));
    assertEquals("2020-03-07T22:03:38+0000", editedNote.getString("addDate"));

    verify(dtMock).updateTimerStatus(noteCaptor.capture());
    Note updatedNote = noteCaptor.getValue();
    assertEquals("active", updatedNote.status);
    assertEquals("I am sam", updatedNote.body);
    assertEquals("doorBoard3_ID", updatedNote.doorBoardID);
    assertEquals("2020-03-07T22:03:38+0000", updatedNote.addDate);
  }

  @Test
  public void AddExpirationToNote() throws IOException {
    // This is... a little ugly. And relies on something else working. But there
    // isn't a great way of knowing
    // the ID of another notice without an expiration date.

    ArgumentCaptor<Note> noteCaptor = ArgumentCaptor.forClass(Note.class);

    String testNewNote = "{ "
      + "\"body\": \"Test Body\", "
      + "\"addDate\": \"2020-03-07T22:03:38+0000\", "
      + "\"status\": \"active\" "
      + "}";

    mockReq.setBodyContent(testNewNote);
    mockReq.setMethod("POST");

    useJwtForNewUser();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/new");

    noteController.addNewNote(ctx);

    String id = jsonMapper.readValue(ctx.resultString(), ObjectNode.class).get("id").asText();
    mockRes.resetAll();

    // We don't need to re-mock the JwtProcessor; the old mock should
    // still work fine.

    mockReq.setBodyContent("{\"expireDate\": \"2021-03-07T22:03:38+0000\"}");
    mockReq.setMethod("PATCH");
    ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", new ObjectId(id).toHexString()));
    noteController.editNote(ctx);

    assertEquals(1, db.getCollection("notes").countDocuments(eq("_id", new ObjectId(id))));

    Document addedNote = db.getCollection("notes").find(eq("_id", new ObjectId(id))).first();
    assertNotNull(addedNote);
    assertEquals("e7fd674c72b76596c75d9f1e", addedNote.getString("doorBoardID"));
    assertEquals("Test Body", addedNote.getString("body"));
    assertEquals("2020-03-07T22:03:38+0000", addedNote.getString("addDate"));
    assertEquals("2021-03-07T22:03:38+0000", addedNote.getString("expireDate"));
    assertEquals("active", addedNote.getString("status"));

    verify(dtMock).updateTimerStatus(noteCaptor.capture());
    Note editedNote = noteCaptor.getValue();
    assertEquals(id, editedNote._id);
    assertEquals("e7fd674c72b76596c75d9f1e", editedNote.doorBoardID);
    assertEquals("Test Body", editedNote.body);
    assertEquals("2020-03-07T22:03:38+0000", editedNote.addDate);
    assertEquals("2021-03-07T22:03:38+0000", editedNote.expireDate);
    assertEquals("active", editedNote.status);

  }

  @Test
  public void ChangingStatusRemovesExpiration() throws IOException {
    String reqBody = "{\"status\": \"draft\"}";
    mockReq.setBodyContent(reqBody);
    mockReq.setMethod("PATCH");

    useJwtForSam();

      Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));
    noteController.editNote(ctx);

    assertEquals(204, mockRes.getStatus());

    assertEquals(1, db.getCollection("notes").countDocuments(eq("_id", samsNoteId)));

    Document editedNote = db.getCollection("notes").find(eq("_id", samsNoteId)).first();
    assertNotNull(editedNote);

    assertEquals("draft", editedNote.getString("status"));
    assertNull(editedNote.getString("expireDate"));

    assertEquals("I am sam", editedNote.getString("body"));
    assertEquals("doorBoard3_ID", editedNote.getString("doorBoardID"));
    assertEquals("2020-03-07T22:03:38+0000", editedNote.getString("addDate"));

    verify(dtMock).updateTimerStatus(any(Note.class));

  }

  @Test
  public void AddExpirationToInactive() throws IOException {

    String testNewNote = "{ "
      + "\"body\": \"Test Body\", "
      + "\"addDate\": \"2020-03-07T22:03:38+0000\", "
      + "\"status\": \"template\""
      + "}";

    mockReq.setBodyContent(testNewNote);
    mockReq.setMethod("POST");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/new");

    noteController.addNewNote(ctx);

    String id = jsonMapper.readValue(ctx.resultString(), ObjectNode.class).get("id").asText();
    mockRes.resetAll();

    mockReq.setBodyContent("{\"expireDate\": \"2021-03-07T22:03:38+0000\"}");
    mockReq.setMethod("PATCH");
    Context ctx2 = ContextUtil.init(mockReq, mockRes, "api/notes/:id",
        ImmutableMap.of("id", new ObjectId(id).toHexString()));

    assertThrows(ConflictResponse.class, () -> {
      noteController.editNote(ctx2);
    });

  }

  @Test
  public void AddExpirationAndDeactivate() throws IOException {
    mockReq.setBodyContent("{\"expireDate\": \"2021-03-07T22:03:38+0000\", \"status\": \"draft\"}");
    mockReq.setMethod("PATCH");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));

    assertThrows(ConflictResponse.class, () -> {
      noteController.editNote(ctx);
    });

  }

  // Internal helper functions
  @Test
  public void FlagSinglePost() throws IOException {
    noteController.flagOneForDeletion(samsNoteId.toHexString());

    assertEquals("deleted", db.getCollection("notes").find(eq("_id", samsNoteId)).first().getString("status"));
    verify(dtMock).updateTimerStatus(any(Note.class));
  }

  @Test
  public void PurgeSinglePost() throws IOException {
    noteController.singleDelete(samsNoteId.toHexString());

    assertEquals(0, db.getCollection("notes").countDocuments(eq("_id", samsNoteId)));
    verify(dtMock).clearKey(samsNoteId.toHexString());
  }
}
