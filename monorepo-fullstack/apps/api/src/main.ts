// this is the imported symbol from the shared library
import { PROJECT_NAME } from '@engineeringmindscape/shared';

export const handler = (event: unknown, context: unknown, callback: (err: null, resp: Record<string, number|string|Record<string, string>>) => void) => {
  const response = {
    statusCode: 200,
    headers: {
      "Access-Control-Allow-Origin": "*",
    },
    body: JSON.stringify({
      api: PROJECT_NAME,
    }),
  };

  callback(null, response);
};
