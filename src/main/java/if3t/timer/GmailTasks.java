package if3t.timer;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.google.api.services.gmail.model.Message;

import if3t.apis.FacebookUtil;
import if3t.apis.GmailUtil;
import if3t.apis.GoogleCalendarUtil;
import if3t.apis.TwitterUtil;
import if3t.entities.ActionIngredient;
import if3t.entities.Authorization;
import if3t.entities.Channel;
import if3t.entities.ChannelStatus;
import if3t.entities.ParametersActions;
import if3t.entities.Recipe;
import if3t.entities.TriggerIngredient;
import if3t.entities.User;
import if3t.services.ActionIngredientService;
import if3t.services.AuthorizationService;
import if3t.services.ChannelStatusService;
import if3t.services.RecipeService;
import if3t.services.TriggerIngredientService;

@Component
public class GmailTasks {

	@Autowired
	private GmailUtil gmailUtil;
	@Autowired
	private GoogleCalendarUtil gCalendarUtil;
	@Autowired
	private TwitterUtil twitterUtil;
	@Autowired
    private RecipeService recipeService;
	@Autowired
	private FacebookUtil facebookUtil;
	@Autowired
	private TriggerIngredientService triggerIngredientService;
	@Autowired
	private ActionIngredientService actionIngredientService;
	@Autowired
	private AuthorizationService authService;
	@Autowired
	private ChannelStatusService channelStatusService;
	@Value("${app.scheduler.value}")
	private long rate;
	@Value("${app.timezone}")
	private String zone;

	@Scheduled(fixedRateString = "${app.scheduler.value}")
	public void gmailScheduler(){
		TimeZone timezone = TimeZone.getTimeZone(zone);

		List<Recipe> gmailTriggerRecipes = recipeService.getEnabledRecipesByTriggerChannel("gmail");
		for(Recipe recipe: gmailTriggerRecipes){
			try{
				User user = recipe.getUser();
				Channel triggerChannel = recipe.getTrigger().getChannel();
				Channel actionChannel = recipe.getAction().getChannel();
				Authorization triggerAuth = authService.getAuthorization(user.getId(), triggerChannel.getKeyword());
				Authorization actionAuth = authService.getAuthorization(user.getId(), actionChannel.getKeyword());

				//Checking if the access token of the trigger channel is expired
				Calendar now = Calendar.getInstance();
				if(triggerAuth == null || triggerAuth.getExpireDate()*1000 <= now.getTimeInMillis()){
					continue;
				}

				//Checking if the access token of the action channel is not present
				if(actionAuth == null)
					continue;

				List<TriggerIngredient> triggerIngredients = triggerIngredientService.getRecipeTriggerIngredients(recipe.getId());

				Long timestamp = 0l;

				ChannelStatus channelStatus = channelStatusService.readChannelStatusByRecipeId(recipe.getId());
				if(channelStatus == null){
					timestamp = Calendar.getInstance().getTimeInMillis()- (rate);
					channelStatus = channelStatusService.createNewChannelStatus(recipe.getId(), timestamp);
				}
				else
					timestamp = channelStatus.getSinceRef();
	
				List<Message> messages = gmailUtil.checkEmailReceived(triggerAuth, triggerIngredients, recipe);

				if(messages.size() > 0){
					messages = gmailUtil.getMessages(triggerAuth, messages);
					List<ActionIngredient> actionIngredients = actionIngredientService.getRecipeActionIngredients(recipe.getId());

					//Checking if the access token of the action channel is expired
					now = Calendar.getInstance();
					if(actionAuth.getExpireDate()*1000 <= now.getTimeInMillis())
						continue;
	
					switch(recipe.getAction().getChannel().getKeyword()){
						case "gmail" :
							for(Message message : messages){
								System.out.println(message.toPrettyString());
								String to = "";
								String subject = "";
								String body = "";
	
								for(ActionIngredient actionIngredient: actionIngredients){
									ParametersActions actionParam = actionIngredient.getParam();
	
									switch(actionParam.getKeyword()){
										case "to_address" :
											to = actionIngredient.getValue();
											break;
										case "subject" :
											subject = gmailUtil.validateAndReplaceKeywords(actionIngredient.getValue(), actionParam.getMaxLength(), message);
											break;
										case "body" :
											body = gmailUtil.validateAndReplaceKeywords(actionIngredient.getValue(), actionParam.getMaxLength(), message);
											break;	
									}
								}
								gmailUtil.sendEmail(to, subject, body, actionAuth);
							}
							break;
						case "calendar" :
							for(Message message : messages){
								String title = "";
								String location = "";
								String description = "";
								String startDateString = "";
								String endDateString = "";
								String startTimeString = "";
								String endTimeString = "";
								
								for(ActionIngredient actionIngredient: actionIngredients){
									ParametersActions actionParam = actionIngredient.getParam();
			
									switch(actionParam.getKeyword()){
										case "start_date" :
											startDateString = actionIngredient.getValue();
											break;
										case "end_date" :
											endDateString = actionIngredient.getValue();
											break;
										case "start_time" :
											startTimeString = actionIngredient.getValue();
											break;
										case "end_time" :
											endTimeString = actionIngredient.getValue();
											break;
										case "title" :
											title = gmailUtil.validateAndReplaceKeywords(actionIngredient.getValue(), actionParam.getMaxLength(), message);
											break;
										case "description" :
											description = gmailUtil.validateAndReplaceKeywords(actionIngredient.getValue(), actionParam.getMaxLength(), message);
											break;
										case "location" :
											location = actionIngredient.getValue();
											break;
									}
								}
								
								SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm");
		
								String startDate = startDateString + " " + startTimeString;
								String endDate = endDateString + " " + endTimeString;
								Calendar start = Calendar.getInstance();
								Calendar end = Calendar.getInstance();
								start.setTime(format.parse(startDate));
								start.setTimeZone(timezone);
								end.setTime(format.parse(endDate));
								end.setTimeZone(timezone);
		
								gCalendarUtil.createEvent(start, end, title, description, location, triggerAuth);
							}
							break;
						case "facebook" :
							for(Message message : messages){
								String post = "";
								for(ActionIngredient actionIngredient: actionIngredients){
									ParametersActions actionParam = actionIngredient.getParam();
		
									if(actionParam.getKeyword().equals("post"))
										post = gmailUtil.validateAndReplaceKeywords(actionIngredient.getValue(), actionParam.getMaxLength(), message);
								}
								facebookUtil.publish_new_post(post, actionAuth.getAccessToken());
							}
							break;
						case "twitter" :
							for(Message message : messages){
								String tweet = "";
								String hashtag = "";
								for(ActionIngredient actionIngredient: actionIngredients){
									ParametersActions actionParam = actionIngredient.getParam();
		
									switch(actionParam.getKeyword()){
										case "tweet" :
											tweet = gmailUtil.validateAndReplaceKeywords(actionIngredient.getValue(), actionParam.getMaxLength(), message);
											break;
										case "hashtag" :
											hashtag = gmailUtil.validateAndReplaceKeywords(actionIngredient.getValue(), actionParam.getMaxLength(), message);
											break;
									}
								}
								twitterUtil.postTweet(user.getId(), actionAuth, tweet, hashtag);
							}
							break;
					}
				}
			}catch (Exception e){
				e.printStackTrace();
				continue;
			}
		}

	}
	
	
}