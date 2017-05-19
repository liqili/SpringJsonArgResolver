import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

public class JsonPathArgumentResolver implements HandlerMethodArgumentResolver {

	private static final String JSONBODYATTRIBUTE = "JSON_REQUEST_BODY";

	private static final String SEPARATOR = "/";

	private ObjectMapper om = new ObjectMapper();

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(JsonArg.class);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
			WebDataBinderFactory binderFactory) throws Exception {

		String paraValue = parameter.getParameterAnnotation(JsonArg.class)
				.value();
		if (StringUtils.isEmpty(paraValue)) {
			throw new Exception(
					"No value specified for parameter annotation @JsonArg!");
		}

		String jsonBody = getRequestBody(webRequest);

		JsonNode rootNode = om.readTree(jsonBody);
		String[] pathArr = StringUtils.split(paraValue, SEPARATOR);
		JsonNode node = rootNode;
		for (String path : pathArr) {
			node = node.path(path);
			if (node.isMissingNode()) {
				String defaultValue = parameter.getParameterAnnotation(
						JsonArg.class).defaultValue();
				if (StringUtils.equals(ValueConstants.DEFAULT_NONE,
						defaultValue)) {
					throw new Exception("There is a missing node '" + path
							+ "' found in the path '" + paraValue + "'");
				} else {
					if (StringUtils.isEmpty(defaultValue)) {
						return null;
					} else {
						return om.readValue(defaultValue,
								parameter.getParameterType());
					}
				}
			}
		}
		// JsonNode node = rootNode.path(parameter.getParameterName());
		Class<?> type = parameter.getParameterType();
		if (Collection.class.isAssignableFrom(type)) {
			Type genericType = parameter.getGenericParameterType();
			Class itemClass = Object.class;
			Type itemType = null;
			if ((genericType != null)
					&& (genericType instanceof ParameterizedType)) {
				ParameterizedType ptype = (ParameterizedType) genericType;
				itemType = ptype.getActualTypeArguments()[0];
				if (itemType.getClass().equals(Class.class)) {
					itemClass = (Class) itemType;
				} else {
					itemClass = (Class) ((ParameterizedType) itemType)
							.getRawType();
				}
			}

			CollectionType javaType = om.getTypeFactory()
					.constructCollectionType(
							(Class<? extends Collection>) type, itemClass);
			return om.readValue(node.toString(), javaType);
		} else {
			return om.readValue(node.toString(), type);
		}

	}

	

	private String getRequestBody(NativeWebRequest webRequest) {
		HttpServletRequest servletRequest = webRequest
				.getNativeRequest(HttpServletRequest.class);

		String jsonBody = (String) webRequest.getAttribute(JSONBODYATTRIBUTE,
				NativeWebRequest.SCOPE_REQUEST);
		if (jsonBody == null) {
			try {
				jsonBody = IOUtils.toString(servletRequest.getInputStream());
				webRequest.setAttribute(JSONBODYATTRIBUTE, jsonBody,
						NativeWebRequest.SCOPE_REQUEST);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return jsonBody;

	}

}
